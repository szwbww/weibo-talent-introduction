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
    selectedRuleId: null,
    unmatchedRecords: [],
    unmatchedFiltered: [],
    monitoring: {
        date: null,
        summary: null,
        subTab: "introductions",
        page: 0,
        pageSize: 20,
        rows: [],
        totalCount: 0,
        senderHealth: [],
        lastRefreshedAt: null,
        autoRefreshTimer: null
    }
};

const contextPath = (() => {
    const firstSegment = window.location.pathname.split("/").filter(Boolean)[0];
    return firstSegment ? `/${firstSegment}` : "";
})();

const viewMeta = {
    monitoring: ["邮件监控", "当日活动概览、自动回复全链路、发件账号健康。"],
    accounts: ["邮箱账号", "维护发送账号、权重、限额和连通性。"],
    qa: ["QA 规则", "维护英文关键词规则、自动回复和人工处理策略。"],
    contacts: ["专家联系", "查看联系状态、邮件时间线和人工处理。"],
    unmatched: ["待处理邮件", "人工待办来信队列与专家绑定。"],
    tasks: ["任务记录", "查看定时任务、队列消费和失败记录。"],
    simulator: ["自动回复模拟器", "创建测试联系人并验证自动回复状态机。"]
};

const statusLabels = {
    NEW: "新建",
    INTRO_SENT: "首封已发送",
    WAITING_REPLY: "等待回复",
    INTEREST_CONFIRMED: "已确认意向",
    QA_AUTO_REPLIED: "QA 已自动回复",
    MEETING_SCHEDULING: "会议排期中",
    MEETING_SCHEDULED: "会议已安排",
    MEETING_DONE: "会议已完成",
    MEETING_INVITATION_SENT: "会议邀约已发送",
    WAITING_MEETING_CONFIRMATION: "等待会议确认",
    MATERIALS_REQUESTED: "已请求材料",
    MATERIALS_PARTIAL: "材料部分收到",
    MATERIALS_RECEIVED: "材料已收到",
    COMPANY_MATCHED: "企业已匹配",
    APPLICATION_PREPARING: "申请准备中",
    VIDEO_REQUESTED: "已请求视频",
    VIDEO_RECEIVED: "视频已收到",
    COMMITMENT_REQUESTED: "已请求承诺书",
    COMMITMENT_RECEIVED: "承诺书已收到",
    SUBMITTED: "已提交",
    RESULT_PENDING: "等待结果",
    REJECTED_THIS_ROUND: "本轮未通过",
    NEXT_ROUND_FOLLOW_UP: "下一轮跟进",
    MANUAL_HANDOFF: "已转人工",
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
    MEETING_INVITATION: "会议邀约",
    MEETING_CONFIRMATION: "会议确认"
};

const triggeredByLabels = {
    SYSTEM: "自动",
    OPERATOR: "人工"
};

const promotionStatusLabels = {
    PENDING: "处理中",
    SUCCESS: "成功",
    FAILED: "失败",
    REVERTED: "已回退"
};

const documentTypeLabels = {
    CV: "简历",
    PASSPORT: "护照",
    PHD_DEGREE: "博士学位",
    MASTER_DEGREE: "硕士学位",
    BACHELOR_DEGREE: "学士学位",
    EMPLOYMENT_PROOF: "工作证明",
    PATENT_PROOF: "专利证明",
    AWARD_PROOF: "奖项证明",
    PUBLICATION_LIST: "论文清单",
    PPT: "PPT",
    VIDEO: "视频",
    COMMITMENT: "承诺书",
    OTHER: "其他材料"
};

const documentStatusLabels = {
    PENDING_REVIEW: "待审核",
    ACCEPTED: "已通过",
    REJECTED: "已驳回"
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

function labelDocumentType(value) {
    return documentTypeLabels[value] || value || "";
}

function labelDocumentStatus(value) {
    return documentStatusLabels[value] || value || "";
}

function formatStatusTransition(history) {
    const from = history.fromStatus ? labelStatus(history.fromStatus) : "初始";
    return `${from} → ${labelStatus(history.toStatus)}`;
}

function formatFileSize(size) {
    if (!size) return "0 B";
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
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

const conversationStatusLabels = {
    NEW: "新建",
    INTRO_SENT: "首封已发送",
    WAITING_REPLY: "等待回复",
    INTEREST_CONFIRMED: "已确认意向",
    QA_AUTO_REPLIED: "QA 已自动回复",
    MEETING_SCHEDULING: "会议排期中",
    MEETING_SCHEDULED: "会议已安排",
    MEETING_DONE: "会议已完成",
    MEETING_INVITATION_SENT: "会议邀约已发送",
    WAITING_MEETING_CONFIRMATION: "等待会议确认",
    MATERIALS_REQUESTED: "已请求材料",
    MATERIALS_PARTIAL: "材料部分收到",
    MATERIALS_RECEIVED: "材料已收到",
    COMPANY_MATCHED: "企业已匹配",
    APPLICATION_PREPARING: "申请准备中",
    VIDEO_REQUESTED: "已请求视频",
    VIDEO_RECEIVED: "视频已收到",
    COMMITMENT_REQUESTED: "已请求承诺书",
    COMMITMENT_RECEIVED: "承诺书已收到",
    SUBMITTED: "已提交",
    RESULT_PENDING: "等待结果",
    REJECTED_THIS_ROUND: "本轮未通过",
    NEXT_ROUND_FOLLOW_UP: "下一轮跟进",
    MANUAL_HANDOFF: "已转人工"
};

function optionsFromLabels(labels, includeBlank = false, blankLabel = "全部") {
    const opts = [];
    if (includeBlank) opts.push(`<option value="">${escapeHtml(blankLabel)}</option>`);
    for (const [value, label] of Object.entries(labels)) {
        opts.push(`<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`);
    }
    return opts.join("");
}

function numberValue(value, fallback = 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

function setView(view) {
    if (state.monitoring.autoRefreshTimer && view !== "monitoring") {
        clearTimeout(state.monitoring.autoRefreshTimer);
        state.monitoring.autoRefreshTimer = null;
    }
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
        if (state.view === "unmatched") await loadUnmatched();
        if (state.view === "tasks") await loadTasks();
        if (state.view === "monitoring") await loadMonitoring();
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

    // Category filter + form select
    const filter = $("#qaCategoryFilter");
    const filterValue = filter.value;
    filter.innerHTML = `<option value="">全部分类</option>` + categories.map((category) => `
        <option value="${category.id}">${escapeHtml(category.categoryName)}</option>
    `).join("");
    filter.value = filterValue;
    const formSelect = $("#qaRuleForm").categoryId;
    const formSelectValue = formSelect.value;
    formSelect.innerHTML = `<option value="">请选择分类</option>` + categories.map((category) => `
        <option value="${category.id}">${escapeHtml(category.categoryName)}</option>
    `).join("");
    formSelect.value = formSelectValue;

    renderQaRulesTable();
}

function renderQaRulesTable() {
    const filterValue = $("#qaCategoryFilter")?.value || "";
    const rows = state.qaRules.filter((rule) =>
        !filterValue || String(rule.categoryId) === filterValue
    );
    $("#qaRulesTable").innerHTML = rows.map((rule) => {
        const displayName = rule.displayName?.trim();
        const nameCell = displayName
            ? `<strong>${escapeHtml(displayName)}</strong>`
            : `<span class="muted">（未设置中文名）</span>`;
        return `
        <tr>
            <td>${rule.id}</td>
            <td>${nameCell}</td>
            <td>${escapeHtml(rule.categoryName || rule.categoryCode || rule.categoryId)}</td>
            <td>${escapeHtml(rule.replySubject || "")}</td>
            <td class="muted-cell">${escapeHtml(rule.keywords)}</td>
            <td>${rule.priority}</td>
            <td>${badge(rule.enabled ? "启用" : "禁用", rule.enabled ? "ok" : "error")}</td>
            <td class="actions">
                <button class="button" data-action="edit-rule" data-id="${rule.id}">编辑</button>
                <button class="button" data-action="toggle-rule" data-id="${rule.id}" data-enabled="${rule.enabled}">
                    ${rule.enabled ? "禁用" : "启用"}
                </button>
            </td>
        </tr>
    `;
    }).join("") || `<tr><td colspan="8" class="muted" style="text-align:center; padding:20px;">暂无 QA 规则</td></tr>`;
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

function showQaRuleEditor() {
    $("#qaRuleModal").hidden = false;
    document.body.classList.add("modal-open");
}

function hideQaRuleEditor() {
    const form = $("#qaRuleForm");
    form.reset();
    $("#qaRuleModal").hidden = true;
    document.body.classList.remove("modal-open");
    state.selectedRuleId = null;
}

function fillQaRuleForm(rule) {
    const form = $("#qaRuleForm");
    showQaRuleEditor();
    state.selectedRuleId = rule?.id || null;
    $("#qaRuleEditorTitle").textContent = rule
        ? `编辑规则：${rule.displayName || `#${rule.id}`}`
        : "新增 QA 规则";
    form.id.value = rule?.id || "";
    form.displayName.value = rule?.displayName || "";
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
        displayName: values.displayName?.trim() || null,
        autoReplyEnabled: form.autoReplyEnabled.checked,
        handoffRequired: form.handoffRequired.checked,
        enabled: form.enabled.checked
    };
    const path = state.selectedRuleId ? `/api/qa/rules/${state.selectedRuleId}` : "/api/qa/rules";
    await api(path, { method: state.selectedRuleId ? "PUT" : "POST", body: JSON.stringify(payload) });
    showStatus("QA 规则已保存");
    hideQaRuleEditor();
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
    const level = $("#expertIndexLevel").value;
    const size = $("#expertIndexSize").value || "50";
    const status = $("#contactStatusFilter")?.value || "";
    const needsAttention = $("#contactNeedsAttentionFilter")?.value || "";

    let contacts = [];
    if (status || needsAttention) {
        const params = new URLSearchParams();
        if (status) params.set("status", status);
        if (needsAttention) params.set("needsAttention", needsAttention);
        const rawContacts = await api(`/api/expert-contacts?${params}`);
        contacts = rawContacts.map(c => ({
            orcidId: c.orcidId,
            email: c.expertEmail,
            displayName: c.expertName,
            indexLevel: c.currentIndexLevel,
            indexLevelName: indexLevelLabels[c.currentIndexLevel] || c.currentIndexLevel,
            contactId: c.id,
            contactStatus: c.currentStatus,
            needsManualAttention: c.needsManualAttention,
            country: "",
            employment: "",
            keyword: ""
        }));
    } else {
        const params = new URLSearchParams();
        params.set("level", level);
        params.set("size", size);
        const rawExperts = await api(`/api/experts?${params}`);
        contacts = rawExperts.map(e => ({
            orcidId: e.orcidId,
            email: e.email,
            displayName: e.displayName,
            indexLevel: e.indexLevel,
            indexLevelName: e.indexLevelName,
            contactId: e.contactId,
            contactStatus: e.contactStatus,
            needsManualAttention: e.needsManualAttention,
            country: e.country,
            employment: e.employment,
            keyword: e.keyword
        }));
    }
    state.contacts = contacts;

    $("#contactList").innerHTML = state.contacts.map((contact) => {
        const status = contact.contactId ? labelStatus(contact.contactStatus) : "未联系";
        const statusType = contact.contactStatus === "MANUAL_HANDOFF"
            ? "warn"
            : contact.contactId
                ? "ok"
                    : "";
        const needsAttentionClass = contact.needsManualAttention ? "needs-attention" : "";
        return `
        <div class="list-item expert-list-item ${needsAttentionClass} ${state.selectedExpertOrcid === contact.orcidId ? "active" : ""}" data-action="select-expert" data-orcid="${escapeHtml(contact.orcidId)}" data-contact-id="${contact.contactId || ""}">
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
    $("#contactHeadActions").hidden = true;
    $("#contactHeadActions").innerHTML = "";
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
                        ${badge(expert.contactStatus ? labelStatus(expert.contactStatus) : "未联系", expert.contactStatus === "MANUAL_HANDOFF" ? "warn" : expert.contactId ? "ok" : "")}
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

function renderMeetingSchedule(detail) {
    const schedules = detail.meetingSchedules || [];
    const contactId = detail.contact.id;
    const activeSchedule = schedules.find(s => s.meetingStatus === "PENDING" || s.meetingStatus === "CONFIRMED");

    if (!activeSchedule) {
        const isSchedulingState = detail.contact.currentStatus === "MEETING_SCHEDULING" || detail.contact.currentStatus === "INTEREST_CONFIRMED" || detail.contact.currentStatus === "WAITING_REPLY";
        if (!isSchedulingState) return "";
        return `
            <div class="meeting-schedule-panel card span-all" style="margin-top: 16px; border: 1px dashed #d1d5db; border-radius: 8px; padding: 16px;">
                <div class="panel-header" style="display: flex; align-items: center; gap: 6px; color: #4b5563; margin-bottom: 12px;">
                    <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    <h3 style="margin: 0; font-size: 14px; font-weight: 600;">会议日程安排</h3>
                </div>
                <div class="panel-body empty-state" style="text-align: center; padding: 16px;">
                    <p style="color: #6b7280; margin-bottom: 12px; font-size: 13px;">目前没有活动的会议安排。</p>
                    <button class="button primary" data-action="initiate-meeting-schedule" data-id="${contactId}">
                        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                        发起会议排期
                    </button>
                </div>
            </div>
        `;
    }

    const isPending = activeSchedule.meetingStatus === "PENDING";
    const statusClass = isPending ? "status-pending" : "status-confirmed";
    const statusText = isPending ? "安排中" : "已确认";

    return `
        <div class="meeting-schedule-panel card span-all ${statusClass}" style="margin-top: 16px; border: 1px solid ${isPending ? '#fde68a' : '#a7f3d0'}; background: ${isPending ? '#fffbef' : '#f0fdf4'}; border-radius: 8px; padding: 16px;">
            <div class="panel-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px dashed ${isPending ? '#fcd34d' : '#6ee7b7'};">
                <div class="header-title" style="display: flex; align-items: center; gap: 6px; color: ${isPending ? '#92400e' : '#065f46'};">
                    <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    <h3 style="margin: 0; font-size: 14px; font-weight: 600;">会议日程排期</h3>
                </div>
                <span class="badge ${isPending ? 'warn' : 'ok'}">${statusText}</span>
            </div>
            <div class="panel-body">
                ${activeSchedule.expertAvailableText ? `
                    <div class="form-group readonly" style="margin-bottom: 12px;">
                        <label style="font-weight: 600; font-size: 12px; color: #4b5563; display: block; margin-bottom: 4px;">专家可沟通时间 (邮件提取)</label>
                        <div class="pre-text" style="background: rgba(0,0,0,0.03); padding: 8px; border-radius: 4px; font-size: 13px; font-family: monospace; white-space: pre-wrap; color: #1f2937;">${escapeHtml(activeSchedule.expertAvailableText)}</div>
                    </div>
                ` : ""}

                <form id="meetingScheduleForm" data-schedule-id="${activeSchedule.id}" data-contact-id="${contactId}">
                    <div class="form-row" style="display: flex; gap: 12px; margin-bottom: 12px;">
                        <div class="form-group flex-1" style="flex: 1;">
                            <label for="chinaTime" style="font-weight: 600; font-size: 12px; color: #4b5563; display: block; margin-bottom: 4px;">中国时间 (China Time)</label>
                            <input type="text" id="chinaTime" name="chinaTime" value="${escapeHtml(activeSchedule.chinaTime || '')}" placeholder="例如: 2026-06-01 10:00 AM" style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 13px;" ${!isPending ? 'disabled' : ''} required>
                        </div>
                        <div class="form-group flex-1" style="flex: 1;">
                            <label for="meetingTool" style="font-weight: 600; font-size: 12px; color: #4b5563; display: block; margin-bottom: 4px;">会议工具</label>
                            <select id="meetingTool" name="meetingTool" style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 13px; background: white;" ${!isPending ? 'disabled' : ''} required>
                                <option value="Zoom" ${activeSchedule.meetingTool === 'Zoom' ? 'selected' : ''}>Zoom</option>
                                <option value="Teams" ${activeSchedule.meetingTool === 'Teams' ? 'selected' : ''}>Teams</option>
                                <option value="Webex" ${activeSchedule.meetingTool === 'Webex' ? 'selected' : ''}>Webex</option>
                                <option value="Google Meet" ${activeSchedule.meetingTool === 'Google Meet' ? 'selected' : ''}>Google Meet</option>
                                <option value="Other" ${activeSchedule.meetingTool === 'Other' || (!activeSchedule.meetingTool && activeSchedule.meetingTool !== 'Zoom' && activeSchedule.meetingTool !== 'Teams' && activeSchedule.meetingTool !== 'Webex' && activeSchedule.meetingTool !== 'Google Meet') ? 'selected' : ''}>Other</option>
                            </select>
                        </div>
                    </div>
                    <div class="form-group" style="margin-bottom: 12px;">
                        <label for="meetingLink" style="font-weight: 600; font-size: 12px; color: #4b5563; display: block; margin-bottom: 4px;">会议链接 (Meeting Link)</label>
                        <input type="url" id="meetingLink" name="meetingLink" value="${escapeHtml(activeSchedule.meetingLink || '')}" placeholder="https://..." style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 13px;" ${!isPending ? 'disabled' : ''} required>
                    </div>
                    <div class="form-group" style="margin-bottom: 12px;">
                        <label for="note" style="font-weight: 600; font-size: 12px; color: #4b5563; display: block; margin-bottom: 4px;">备注</label>
                        <textarea id="note" name="note" rows="2" placeholder="输入会议注意事项..." style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 13px; resize: vertical;" ${!isPending ? 'disabled' : ''}>${escapeHtml(activeSchedule.note || '')}</textarea>
                    </div>

                    <div class="panel-actions" style="display: flex; gap: 8px; margin-top: 16px; justify-content: flex-end;">
                        ${isPending ? `
                            <button type="button" class="button" data-action="save-meeting-schedule" data-id="${activeSchedule.id}" data-contact-id="${contactId}">仅保存更新</button>
                            <button type="submit" class="button primary" data-action="confirm-meeting-schedule" data-id="${activeSchedule.id}">
                                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                                确认并发送邮件
                            </button>
                            <button type="button" class="button danger" data-action="cancel-meeting-schedule" data-id="${activeSchedule.id}" data-contact-id="${contactId}">取消排期</button>
                        ` : `
                            <button type="button" class="button primary" data-action="complete-meeting-schedule" data-id="${activeSchedule.id}" data-contact-id="${contactId}">
                                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><polyline points="20 6 9 17 4 12"/></svg>
                                标记会议完成
                            </button>
                            <button type="button" class="button danger" data-action="cancel-meeting-schedule" data-id="${activeSchedule.id}" data-contact-id="${contactId}">取消排期</button>
                        `}
                    </div>
                </form>
            </div>
        </div>
    `;
}

async function confirmMeetingSchedule(form) {
    const contactId = form.dataset.contactId;
    const scheduleId = form.dataset.scheduleId;
    const values = formValues(form);
    await api(`/api/expert-contacts/${contactId}/meeting-schedules/${scheduleId}/confirm-and-email`, {
        method: "POST",
        body: JSON.stringify({
            chinaTime: values.chinaTime,
            meetingTool: values.meetingTool,
            meetingLink: values.meetingLink,
            note: values.note
        })
    });
    showStatus("会议已确认并已向专家发送确认邮件");
    await loadContactDetail(contactId);
    await loadContacts();
}

function renderManualAttentionBanner(contact) {
    if (!contact.needsManualAttention) return "";
    const reasonType = contact.latestManualReviewReasonType;
    const messages = {
        QA_NO_MATCH: "该回复未命中任何 QA 规则，自动回复已暂停，请人工答复。",
        NOT_INTERESTED: "系统判定该专家无意向，请人工确认后退回到原始层或恢复自动回复。",
        UNCLEAR_INTENT: "该回复意图无法识别，自动回复已暂停，请人工查阅。"
    };
    const text = messages[reasonType] || "该联系需要人工介入。";
    return `
        <div class="attention-banner" data-reason="${reasonType || ''}">
            <strong>⚠ 需要人工处理</strong>
            <span>${escapeHtml(text)}</span>
            <a href="javascript:void 0" data-action="goto-manual-queue">跳转人工处理队列</a>
        </div>
    `;
}

function renderIndexLevelButtons(contact) {
    switch (contact.currentIndexLevel) {
        case "APPLICATION":
            return `<button class="button danger" data-action="demote-to-raw" data-id="${contact.id}"><span>退回到原始层</span></button>`;
        case "CANDIDATE":
            return `
                <button class="button" data-action="promote-to-application" data-id="${contact.id}"><span>加入有效层</span></button>
                <button class="button danger" data-action="demote-to-raw" data-id="${contact.id}"><span>退回到原始层</span></button>
            `;
        case "RAW":
            return `
                <button class="button" data-action="promote-to-candidate" data-id="${contact.id}"><span>加入筛选层</span></button>
                <button class="button" data-action="promote-to-application" data-id="${contact.id}"><span>加入有效层</span></button>
            `;
        default:
            return "";
    }
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
            <button class="button" data-action="toggle-reply-mode"
                    data-id="${contact.id}"
                    data-enabled="${contact.autoReplyEnabled}">
                <span>${contact.autoReplyEnabled ? "切换为人工回复" : "切换为自动回复"}</span>
            </button>
            ${renderIndexLevelButtons(contact)}
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
                <span>发送邮件</span>
            </button>
        </div>
    `;

    const banner = renderManualAttentionBanner(contact);
    const contactDetail = $("#contactDetail");
    contactDetail.classList.remove("detail-empty");
    contactDetail.scrollTop = 0;
    contactDetail.innerHTML = `
        ${banner}
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

            ${renderMeetingSchedule(detail)}

            <div class="metadata-grid">
                <!-- Stage Status Card -->
                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        <span>阶段状态</span>
                    </div>
                    <div class="metadata-card-value">
                        ${badge(labelStatus(contact.currentStatus), contact.currentStatus === "MANUAL_HANDOFF" ? "warn" : "ok")}
                    </div>
                </div>

                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
                        <span>推荐下一步</span>
                    </div>
                    <div class="metadata-card-value next-action-text">
                        ${escapeHtml(detail.recommendedNextAction || "请人工确认下一步动作。")}
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

                <div class="metadata-card span-all" id="emailAliasPlaceholder" style="min-height: 60px;">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                        <span>邮箱别名</span>
                    </div>
                    <div class="metadata-card-value" style="font-size: 12px; color: var(--text-muted);">加载中...</div>
                </div>

                <div class="metadata-card span-all">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
                        <span>阶段流转历史</span>
                    </div>
                    <div class="metadata-card-value status-history-list">
                        ${(detail.statusHistory || []).length ? detail.statusHistory.slice().reverse().map((history) => `
                            <div class="status-history-row">
                                <div>
                                    <strong>${escapeHtml(formatStatusTransition(history))}</strong>
                                    <span>${escapeHtml(history.reason || "-")} · ${escapeHtml(history.source || "-")}</span>
                                </div>
                                <span>${escapeHtml(history.createdAt || "")}</span>
                            </div>
                        `).join("") : "<span>暂无阶段流转记录。"}
                    </div>
                </div>
            </div>

        </div>
    `;
    requestAnimationFrame(() => {
        contactDetail.scrollTop = 0;
    });
    if (contact.id) {
        loadEmailAliases(contact.id, contact);
    }
}

async function loadEmailAliases(contactId, contact) {
    const placeholder = $("#emailAliasPlaceholder");
    if (!placeholder) return;
    try {
        const aliases = await api(`/api/expert-contacts/${contactId}/email-aliases`);
        const aliasTable = aliases.length ? `
            <table style="width: 100%; font-size: 12px; margin-top: 4px;">
                <thead>
                    <tr>
                        <th style="padding: 4px 6px; text-align: left; font-size: 11px;">别名邮箱</th>
                        <th style="padding: 4px 6px; text-align: left; font-size: 11px;">来源</th>
                        <th style="padding: 4px 6px; text-align: left; font-size: 11px;">确认</th>
                        <th style="padding: 4px 6px; text-align: left; font-size: 11px;">创建时间</th>
                        <th style="padding: 4px 6px; text-align: left; font-size: 11px;">操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${aliases.map((alias) => `
                    <tr>
                        <td style="padding: 4px 6px;">${escapeHtml(alias.email)}</td>
                        <td style="padding: 4px 6px;">${escapeHtml(alias.source)}</td>
                        <td style="padding: 4px 6px;">${alias.verified ? badge("已确认", "ok") : badge("未确认", "warn")}</td>
                        <td style="padding: 4px 6px;">${escapeHtml(alias.createdAt || "")}</td>
                        <td style="padding: 4px 6px;">
                            <button class="button small danger" data-action="delete-alias" data-alias-id="${alias.id}" data-contact-id="${contactId}" style="height: 28px; min-height: 28px; padding: 0 8px; font-size: 11px;">移除</button>
                        </td>
                    </tr>`).join("")}
                </tbody>
            </table>` : '<p style="color: var(--text-muted); font-size: 12px; margin: 4px 0 0;">暂无别名。</p>';
        const mainEmail = contact.expertEmail || "";
        placeholder.innerHTML = `
            <div class="metadata-card-header">
                <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                <span>邮箱别名</span>
            </div>
            <div style="font-size: 13px; color: var(--text-main); margin-bottom: 4px;">
                <strong>主邮箱:</strong> ${escapeHtml(mainEmail)}
            </div>
            ${aliasTable}
            <div class="add-alias-row" style="display: flex; gap: 8px; align-items: center; margin-top: 8px;">
                <input id="newAliasEmail" placeholder="输入邮箱地址" style="flex: 1; height: 34px; min-height: 34px; font-size: 12px;">
                <button class="button primary small" data-action="add-alias" data-contact-id="${contactId}" style="height: 34px; min-height: 34px; padding: 0 12px; font-size: 12px;">添加别名</button>
            </div>
        `;
    } catch (e) {
        placeholder.innerHTML = `
            <div class="metadata-card-header"><span>邮箱别名</span></div>
            <p style="color: var(--text-muted); font-size: 12px;">加载失败。</p>`;
    }
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
    if (action === "select-contact") {
        await loadContactDetail(id);
        return;
    }
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
        return;
    }
    if (action === "toggle-reply-mode") {
        const enabled = element.dataset.enabled === "true";
        if (enabled) {
            const payload = await openActionDialog("switch-to-manual");
            if (!payload) return;
            await api(`/api/expert-contacts/${id}/switch-to-manual`, {
                method: "POST",
                body: JSON.stringify({ reason: payload.reason, note: payload.note })
            });
            showStatus("已切换为人工回复");
        } else {
            const payload = await openActionDialog("switch-to-auto");
            if (!payload) return;
            await api(`/api/expert-contacts/${id}/switch-to-auto`, {
                method: "POST",
                body: JSON.stringify({ note: payload.note })
            });
            showStatus("已恢复自动回复");
        }
        await loadContactDetail(id);
        await loadContacts();
        return;
    }
    if (action === "promote-to-candidate") {
        await api(`/api/expert-contacts/${id}/promote-to-candidate`, { method: "POST" });
        showStatus("已加入筛选层");
        await loadContactDetail(id);
        await loadContacts();
        return;
    }
    if (action === "promote-to-application") {
        await api(`/api/expert-contacts/${id}/promote-to-application`, { method: "POST" });
        showStatus("已加入有效层");
        await loadContactDetail(id);
        await loadContacts();
        return;
    }
    if (action === "demote-to-raw") {
        const confirmDemote = await openActionDialog("confirm", { message: "确定要将此专家退回到原始层吗？此操作会同步在应用与候选Elasticsearch索引中删除该数据。" });
        if (!confirmDemote) return;
        await api(`/api/expert-contacts/${id}/demote-to-raw`, { method: "POST" });
        showStatus("已退回到原始层");
        await loadContactDetail(id);
        await loadContacts();
        return;
    }
    if (action === "initiate-meeting-schedule") {
        const payload = await openActionDialog("initiate-meeting-schedule");
        if (payload === null) return;
        const availableText = payload.availableText;
        await api(`/api/expert-contacts/${id}/meeting-schedules`, {
            method: "POST",
            body: JSON.stringify({
                expertAvailableText: availableText || "手动发起的会议安排",
                expertTimezone: null,
                chinaTime: null,
                meetingTool: "Zoom",
                meetingLink: null,
                note: null
            })
        });
        showStatus("已发起会议排期");
        await loadContactDetail(id);
        return;
    }
    if (action === "save-meeting-schedule") {
        const form = $("#meetingScheduleForm");
        if (!form) return;
        const values = formValues(form);
        const scheduleId = element.dataset.id;
        const contactId = element.dataset.contactId;
        await api(`/api/expert-contacts/${contactId}/meeting-schedules/${scheduleId}`, {
            method: "PUT",
            body: JSON.stringify({
                chinaTime: values.chinaTime,
                meetingTool: values.meetingTool,
                meetingLink: values.meetingLink,
                note: values.note
            })
        });
        showStatus("会议排期已更新");
        await loadContactDetail(contactId);
        return;
    }
    if (action === "cancel-meeting-schedule") {
        const scheduleId = element.dataset.id;
        const contactId = element.dataset.contactId;
        const confirmCancel = await openActionDialog("confirm", { message: "确定要取消当前会议排期吗？" });
        if (!confirmCancel) return;
        await api(`/api/expert-contacts/${contactId}/meeting-schedules/${scheduleId}/cancel`, {
            method: "POST"
        });
        showStatus("会议排期已取消");
        await loadContactDetail(contactId);
        await loadContacts();
        return;
    }
    if (action === "complete-meeting-schedule") {
        const scheduleId = element.dataset.id;
        const contactId = element.dataset.contactId;
        await api(`/api/expert-contacts/${contactId}/meeting-schedules/${scheduleId}/complete`, {
            method: "POST"
        });
        showStatus("已标记会议完成，进入材料准备阶段");
        await loadContactDetail(contactId);
        await loadContacts();
        return;
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

const REASON_TYPE_LABELS = {
    UNMATCHED_CONTACT: "未匹配到专家",
    QA_NO_MATCH: "QA 未命中",
    NOT_INTERESTED: "专家不感兴趣",
    UNCLEAR_INTENT: "意图模糊",
    MANUAL_RESOLVED: "已人工处理"
};
const REASON_TYPE_BADGE_CLASS = {
    UNMATCHED_CONTACT: "info",       // 蓝
    QA_NO_MATCH: "warn",             // 橙
    NOT_INTERESTED: "error",         // 红
    UNCLEAR_INTENT: "warn-yellow"    // 黄
};
const HIGH_PRIORITY_REASON_TYPES = new Set(["NOT_INTERESTED", "QA_NO_MATCH"]);

async function loadUnmatched() {
    const params = new URLSearchParams();
    const reason = $("#unmatchedFilterReasonType").value;
    const email = $("#unmatchedFilterEmail").value.trim();
    const subject = $("#unmatchedFilterSubject").value.trim();
    const pageSize = $("#unmatchedPageSize").value || "20";
    if (reason) params.set("reasonType", reason);
    if (email) params.set("email", email);
    if (subject) params.set("subject", subject);
    params.set("pageSize", pageSize);
    params.set("pageOffset", state.unmatchedPageOffset || "0");
    const data = await api(`/api/mail/unmatched-inbound?${params}`);
    state.unmatchedRecords = data.records || [];
    state.unmatchedCounts = data.countsByReasonType || {};
    renderUnmatchedTable();
    updateUnmatchedBadge(data.countsByReasonType);
}

function updateUnmatchedBadge(counts) {
    if (!counts) {
        api("/api/mail/unmatched-inbound").then(data => {
            updateUnmatchedBadge(data.countsByReasonType);
        }).catch(() => {});
        return;
    }
    const high = Array.from(HIGH_PRIORITY_REASON_TYPES).reduce((s, k) => s + (counts[k] || 0), 0);
    const normal = ["UNMATCHED_CONTACT", "UNCLEAR_INTENT"].reduce((s, k) => s + (counts[k] || 0), 0);
    setBadge("#unmatchedBadgeHigh", high);
    setBadge("#unmatchedBadgeNormal", normal);
}

function setBadge(sel, n) {
    const el = $(sel);
    if (!el) return;
    if (n > 0) { el.textContent = n > 99 ? "99+" : n; el.hidden = false; }
    else el.hidden = true;
}

function renderUnmatchedTable() {
    const rows = state.unmatchedRecords.map((r) => `
        <tr>
            <td>${r.id}</td>
            <td>${escapeHtml(r.fromEmail)}</td>
            <td>${escapeHtml(r.subject || "-")}</td>
            <td>${escapeHtml(r.receivedAt || "")}</td>
            <td>${renderContactLink(r)}</td>
            <td>${badge(REASON_TYPE_LABELS[r.reasonType] || "未知",
                        REASON_TYPE_BADGE_CLASS[r.reasonType] || "warn")}</td>
            <td class="actions">${renderUnmatchedActions(r)}</td>
        </tr>
    `).join("");
    $("#unmatchedTable").innerHTML = rows || `<tr><td colspan="7" class="text-muted" style="text-align: center;">暂无记录</td></tr>`;
}

function renderContactLink(record) {
    if (record.expertContactId) {
        return `<a href="javascript:void 0" data-action="open-contact-from-unmatched" data-id="${record.expertContactId}">
            ${escapeHtml(record.expertName || "(未命名)")}</a>
            <div class="text-muted">${labelStatus(record.expertCurrentStatus)}</div>`;
    }
    return `<span class="text-muted">未匹配</span>`;
}

function renderUnmatchedActions(r) {
    if (!r.expertContactId) {
        return `<button class="button" data-action="view-unmatched" data-id="${r.id}">绑定专家</button>`;
    }
    return `
        <button class="button" data-action="open-contact-from-unmatched" data-id="${r.expertContactId}">查看专家</button>
        <button class="button" data-action="mark-unmatched-resolved" data-id="${r.id}">标记已处理</button>
    `;
}

async function showUnmatchedDetail(id) {
    const data = await api(`/api/mail/unmatched-inbound/${id}`);
    const record = data.record;
    const candidates = data.candidates || [];
    const panel = $("#unmatchedDetailPanel");
    panel.hidden = false;

    const candidatesHtml = candidates.map((c) => `
        <div class="candidate-row" data-contact-id="${c.contactId}">
            <div class="candidate-info">
                <strong>${escapeHtml(c.expertName || "?")}</strong>
                <span>${escapeHtml(c.expertEmail)}</span>
                <span class="text-muted">${escapeHtml(c.orcidId)}</span>
            </div>
            <div class="candidate-meta">
                <span class="badge ${c.confidence >= 80 ? "ok" : "warn"}">${c.reason}</span>
                <span>${c.confidence}%</span>
                <button class="button primary small" data-action="bind-candidate" data-contact-id="${c.contactId}" data-record-id="${record.id}">绑定</button>
            </div>
        </div>
    `).join("") || "<p class='text-muted'>暂无系统推荐，请手动搜索联系人。</p>";

    panel.innerHTML = `
        <div class="panel-head">
            <h2>来信详情与绑定</h2>
            <button class="button secondary" data-action="close-unmatched-detail">关闭</button>
        </div>
        <div class="unmatched-detail-body">
            <div class="metadata-grid">
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>发件邮箱</span></div>
                    <div class="metadata-card-value">${escapeHtml(record.fromEmail)}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>主题</span></div>
                    <div class="metadata-card-value">${escapeHtml(record.subject || "-")}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>Message-ID</span></div>
                    <div class="metadata-card-value" style="font-size: 11px; word-break: break-all;">${escapeHtml(record.messageId || "-")}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>In-Reply-To</span></div>
                    <div class="metadata-card-value" style="font-size: 11px; word-break: break-all;">${escapeHtml(record.inReplyTo || "-")}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>收信时间</span></div>
                    <div class="metadata-card-value">${escapeHtml(record.receivedAt || "")}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>邮箱账号</span></div>
                    <div class="metadata-card-value">${escapeHtml(record.senderAccountCode)}</div>
                </div>
            </div>

            ${record.body ? `
            <div class="detail-section">
                <h3>原始正文</h3>
                <div class="pre">${escapeHtml(record.body)}</div>
            </div>` : ""}

            ${record.cleanedBody ? `
            <div class="detail-section">
                <h3>清洗后正文</h3>
                <div class="pre">${escapeHtml(record.cleanedBody)}</div>
            </div>` : ""}

            <div class="detail-section">
                <h3>候选推荐联系人</h3>
                <div class="candidates-list">${candidatesHtml}</div>
            </div>

            <div class="detail-section">
                <h3>搜索并手动绑定</h3>
                <div class="search-bind-row">
                    <input id="unmatchedSearchQuery" placeholder="输入 ORCID、专家姓名或邮箱搜索" style="flex: 1; min-width: 200px;">
                    <button class="button" data-action="search-candidates" data-record-id="${record.id}">搜索</button>
                </div>
                <div id="unmatchedSearchResults" class="candidates-list"></div>
                <div class="bind-form-row">
                    <label>操作人: <input id="unmatchedResolvedBy" placeholder="输入操作人姓名" required></label>
                    <label class="checkbox-row"><input id="bindPromoteCheck" type="checkbox"> 同时加入有效层</label>
                    <button class="button primary" data-action="bind-manual" data-record-id="${record.id}" id="bindManualBtn" disabled>绑定并添加别名</button>
                </div>
            </div>
        </div>
    `;

    panel.scrollIntoView({ behavior: "smooth" });
}

async function handleUnmatchedAction(element) {
    const action = element.dataset.action;
    const id = element.dataset.id || element.dataset.recordId;

    if (action === "view-unmatched") {
        await showUnmatchedDetail(id);
        return;
    }
    if (action === "close-unmatched-detail") {
        $("#unmatchedDetailPanel").hidden = true;
        return;
    }
    if (action === "open-contact-from-unmatched") {
        setView("contacts");
        await loadContacts();
        await loadContactDetail(Number(id));
        const listItems = $$("#contactList .list-item");
        listItems.forEach(item => {
            const isMatch = Number(item.dataset.contactId) === Number(id);
            item.classList.toggle("active", isMatch);
            if (isMatch) {
                state.selectedExpertOrcid = item.dataset.orcid;
                item.scrollIntoView({ behavior: "smooth", block: "nearest" });
            }
        });
        return;
    }
    if (action === "mark-unmatched-resolved") {
        const payload = await openActionDialog("mark-unmatched-resolved");
        if (!payload) return;
        await api(`/api/mail/unmatched-inbound/${id}/mark-resolved`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        showStatus("已标记为处理完成");
        await loadUnmatched();
        return;
    }
    if (action === "bind-candidate") {
        const contactId = element.dataset.contactId;
        const payload = await openActionDialog("bind-unmatched-contact");
        if (!payload) return;
        const { resolvedBy, promoteToApplication } = payload;
        await api(`/api/mail/unmatched-inbound/${id}/bind`, {
            method: "POST",
            body: JSON.stringify({ contactId: Number(contactId), resolvedBy, promoteToApplication })
        });
        showStatus("已绑定并添加别名");
        $("#unmatchedDetailPanel").hidden = true;
        await loadUnmatched();
        return;
    }
    if (action === "search-candidates") {
        const query = $("#unmatchedSearchQuery").value.trim();
        if (!query) return;
        const results = await api(`/api/mail/unmatched-inbound/search-contacts?query=${encodeURIComponent(query)}`);
        const resultsHtml = results.map((c) => `
            <div class="candidate-row">
                <div class="candidate-info">
                    <strong>${escapeHtml(c.expertName || "?")}</strong>
                    <span>${escapeHtml(c.expertEmail)}</span>
                    <span class="text-muted">${escapeHtml(c.orcidId)}</span>
                </div>
                <div class="candidate-meta">
                    <button class="button primary small" data-action="bind-candidate" data-contact-id="${c.contactId}" data-record-id="${id}">绑定</button>
                </div>
            </div>
        `).join("") || "<p class='text-muted'>未找到匹配的联系人。</p>";
        $("#unmatchedSearchResults").innerHTML = resultsHtml;
        return;
    }
    if (action === "bind-manual") {
        const contactId = element.dataset.contactId;
        if (!contactId) {
            showStatus("请先在搜索结果中选择联系人", "error");
            return;
        }
        const resolvedBy = $("#unmatchedResolvedBy").value.trim();
        if (!resolvedBy) {
            showStatus("请输入操作人姓名", "error");
            return;
        }
        const promote = $("#bindPromoteCheck")?.checked || false;
        await api(`/api/mail/unmatched-inbound/${id}/bind`, {
            method: "POST",
            body: JSON.stringify({ contactId: Number(contactId), resolvedBy, promoteToApplication: promote })
        });
        showStatus("已绑定并添加别名");
        $("#unmatchedDetailPanel").hidden = true;
        await loadUnmatched();
    }
}

function renderEmailAliasSection(contactId) {
    const container = document.createElement("div");
    container.id = "emailAliasSection";
    container.className = "metadata-card span-all";
    container.style.border = "1px solid var(--panel-border)";
    container.style.padding = "14px";
    container.style.borderRadius = "8px";
    api(`/api/expert-contacts/${contactId}/email-aliases`).then((aliases) => {
        container.innerHTML = `
            <div class="metadata-card-header" style="margin-bottom: 8px;">
                <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                <span>邮箱别名</span>
            </div>
            <div style="font-size: 13px; color: var(--text-main); margin-bottom: 8px;">
                <strong>主邮箱:</strong> ${escapeHtml(state.contacts.find(c => c.contactId === contactId)?.email || "")}
            </div>
            ${aliases.length ? `
            <table style="width: 100%; font-size: 12px; margin-bottom: 8px;">
                <thead>
                    <tr>
                        <th style="padding: 4px 6px; text-align: left;">别名邮箱</th>
                        <th style="padding: 4px 6px; text-align: left;">来源</th>
                        <th style="padding: 4px 6px; text-align: left;">确认</th>
                        <th style="padding: 4px 6px; text-align: left;">创建时间</th>
                        <th style="padding: 4px 6px; text-align: left;">操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${aliases.map((alias) => `
                    <tr>
                        <td style="padding: 4px 6px;">${escapeHtml(alias.email)}</td>
                        <td style="padding: 4px 6px;">${escapeHtml(alias.source)}</td>
                        <td style="padding: 4px 6px;">${alias.verified ? badge("已确认", "ok") : badge("未确认", "warn")}</td>
                        <td style="padding: 4px 6px;">${escapeHtml(alias.createdAt || "")}</td>
                        <td style="padding: 4px 6px;">
                            <button class="button small danger" data-action="delete-alias" data-alias-id="${alias.id}" data-contact-id="${contactId}">移除</button>
                        </td>
                    </tr>`).join("")}
                </tbody>
            </table>` : '<p style="color: var(--text-muted); font-size: 12px; margin-bottom: 8px;">暂无别名。</p>'}

            <div class="add-alias-row" style="display: flex; gap: 8px; align-items: center;">
                <input id="newAliasEmail" placeholder="输入邮箱地址" style="flex: 1; height: 34px; min-height: 34px; font-size: 12px;">
                <button class="button primary small" data-action="add-alias" data-contact-id="${contactId}">添加别名</button>
            </div>
        `;
    }).catch(() => {
        container.innerHTML = `<div class="metadata-card-header"><span>邮箱别名</span></div>
            <p style="color: var(--text-muted); font-size: 12px;">加载失败。</p>`;
    });
    return container;
}

async function loadMonitoring() {
    const dateParams = new URLSearchParams();
    if (state.monitoring.date) dateParams.set("date", state.monitoring.date);
    const [summary, senderHealth] = await Promise.all([
        api(`/api/mail-monitoring/summary?${dateParams}`),
        api(`/api/mail-monitoring/sender-accounts?${dateParams}`)
    ]);
    state.monitoring.summary = summary;
    state.monitoring.senderHealth = senderHealth || [];
    renderMonitoringCards();
    renderMonitoringSenderHealth();
    renderMonitoringSenderOptions();
    await loadMonitoringSubTab();
    state.monitoring.lastRefreshedAt = new Date();
    renderMonitoringLastRefreshed();
    scheduleMonitoringAutoRefresh();
}

function monitoringRangeParams() {
    const params = new URLSearchParams();
    const date = state.monitoring.date || new Date().toISOString().slice(0, 10);
    params.set("from", date);
    params.set("to", date);
    params.set("pageSize", state.monitoring.pageSize);
    params.set("pageOffset", state.monitoring.page * state.monitoring.pageSize);
    const sender = $("#monitoringSenderAccount")?.value;
    if (sender && ["introductions", "outbound"].includes(state.monitoring.subTab)) {
        params.set("senderAccountCode", sender);
    }
    return params;
}

function renderMonitoringCards() {
    const s = state.monitoring.summary || {};
    const cards = [
        ["今日介绍邮件", s.introductions],
        ["今日收到回复", s.inboundReplies],
        ["今日回复专家数", s.repliedExperts],
        ["今日自动回复", s.autoReplies],
        ["今日人工外发", s.operatorOutbound],
        ["今日会议邀约", s.meetingInvitations],
        ["今日人工待办新增", s.manualReviewInbound],
        ["今日未匹配来信", s.unmatchedInbound],
        ["今日发送失败", s.failedOutbound],
        ["今日 APPLICATION 晋级", s.applicationPromotions]
    ];
    $("#monitoringCards").innerHTML = cards.map(([label, value]) => `
        <div class="metric-card">
            <div class="metric-label">${escapeHtml(label)}</div>
            <div class="metric-value">${escapeHtml(value ?? 0)}</div>
        </div>
    `).join("");
}

function renderMonitoringSenderOptions() {
    const select = $("#monitoringSenderAccount");
    const selected = select.value;
    select.innerHTML = `<option value="">全部账号</option>` + state.monitoring.senderHealth.map((row) =>
        `<option value="${escapeHtml(row.accountCode)}">${escapeHtml(row.accountCode)}</option>`
    ).join("");
    select.value = selected;
}

function renderMonitoringSenderHealth() {
    const table = $("#monitoringSenderHealthTable");
    table.querySelector("thead").innerHTML = `
        <tr>
            <th>账号</th><th>邮箱</th><th>状态</th><th>今日/上限</th>
            <th>介绍</th><th>自动回复</th><th>失败</th><th>最近发信</th><th>最近收信</th>
        </tr>
    `;
    table.querySelector("tbody").innerHTML = (state.monitoring.senderHealth || []).map((row) => `
        <tr>
            <td><strong>${escapeHtml(row.accountCode)}</strong></td>
            <td>${escapeHtml(row.senderEmail)}</td>
            <td>${badge(row.enabled ? "启用" : "停用", row.enabled ? "ok" : "error")}</td>
            <td>${escapeHtml(row.todaySentCount)}/${escapeHtml(row.dailySendLimit)}</td>
            <td>${escapeHtml(row.introductionCount)}</td>
            <td>${escapeHtml(row.autoReplyCount)}</td>
            <td>${escapeHtml(row.failedCount)}</td>
            <td>${escapeHtml(row.lastSentAt || "-")}</td>
            <td>${escapeHtml(row.lastReceivedAt || "-")}</td>
        </tr>
    `).join("") || `<tr><td colspan="9" class="text-muted" style="text-align:center;">暂无账号数据</td></tr>`;
}

async function loadMonitoringSubTab() {
    const tab = state.monitoring.subTab;
    const params = monitoringRangeParams();
    let url;
    if (tab === "introductions") url = `/api/mail-monitoring/introductions?${params}`;
    if (tab === "inbound") url = `/api/mail-monitoring/inbound?${params}`;
    if (tab === "outbound") url = `/api/mail-monitoring/outbound-replies?${params}`;
    if (tab === "pending") {
        const pendingParams = new URLSearchParams();
        pendingParams.set("pageSize", state.monitoring.pageSize);
        pendingParams.set("pageOffset", state.monitoring.page * state.monitoring.pageSize);
        url = `/api/mail/unmatched-inbound?${pendingParams}`;
    }
    if (tab === "promotions") url = `/api/mail-monitoring/promotions?${params}`;
    const data = await api(url);
    state.monitoring.rows = data.records || [];
    state.monitoring.totalCount = data.totalCount ?? state.monitoring.rows.length;
    renderMonitoringActivityTable();
    renderMonitoringPagination();
}

function renderMonitoringActivityTable() {
    const table = $("#monitoringActivityTable");
    const tab = state.monitoring.subTab;
    const rows = state.monitoring.rows || [];
    const renderEmpty = (colspan) => `<tr><td colspan="${colspan}" class="text-muted" style="text-align:center;">暂无记录</td></tr>`;
    if (tab === "introductions") {
        table.querySelector("thead").innerHTML = `<tr><th>时间</th><th>专家</th><th>账号</th><th>主题</th><th>状态</th><th>阶段</th><th>已回复</th></tr>`;
        table.querySelector("tbody").innerHTML = rows.map((r) => `
            <tr><td>${escapeHtml(r.sentAt || "-")}</td><td>${monitoringContactCell(r)}</td><td>${escapeHtml(r.senderAccountCode || "-")}</td>
            <td>${escapeHtml(r.subject || "-")}</td><td>${badge(r.sendStatus || "-", r.sendStatus === "SUCCESS" ? "ok" : "warn")}</td>
            <td>${escapeHtml(labelStatus(r.contactCurrentStatus) || "-")}</td><td>${escapeHtml(r.replied ? "是" : "否")}</td></tr>
        `).join("") || renderEmpty(7);
        return;
    }
    if (tab === "inbound") {
        table.querySelector("thead").innerHTML = `<tr><th>时间</th><th>专家</th><th>发件邮箱</th><th>主题</th><th>处理</th><th>阶段</th><th>自动回复</th><th>摘要</th></tr>`;
        table.querySelector("tbody").innerHTML = rows.map((r) => `
            <tr><td>${escapeHtml(r.receivedAt || "-")}</td><td>${monitoringContactCell(r)}</td><td>${escapeHtml(r.fromEmail)}</td>
            <td>${escapeHtml(r.subject || "-")}</td><td>${escapeHtml(r.processStatus)} ${r.reasonType ? badge(r.reasonType, "warn") : ""}</td>
            <td>${escapeHtml(labelStatus(r.contactCurrentStatus) || "-")}</td><td>${escapeHtml(r.autoReplyEnabled === false ? "暂停" : "开启")}</td>
            <td>${escapeHtml((r.cleanedBody || "").slice(0, 80))}</td></tr>
        `).join("") || renderEmpty(8);
        return;
    }
    if (tab === "outbound") {
        table.querySelector("thead").innerHTML = `<tr><th>时间</th><th>专家</th><th>触发</th><th>类型</th><th>账号</th><th>主题</th><th>状态</th><th>来源来信</th><th>QA</th></tr>`;
        table.querySelector("tbody").innerHTML = rows.map((r) => `
            <tr><td>${escapeHtml(r.sentAt || "-")}</td><td>${monitoringContactCell(r)}</td><td>${escapeHtml(triggeredByLabels[r.triggeredBy] || r.triggeredBy)}</td>
            <td>${escapeHtml(labelMailType(r.mailType))}</td><td>${escapeHtml(r.senderAccountCode || "-")}</td><td>${escapeHtml(r.subject || "-")}</td>
            <td>${badge(r.sendStatus || "-", r.sendStatus === "SUCCESS" ? "ok" : "warn")}</td><td>${escapeHtml(r.sourceInbound?.subject || "-")}</td>
            <td>${escapeHtml(r.matchedQaRuleDisplayName || r.matchedQaRuleId || "-")}</td></tr>
        `).join("") || renderEmpty(9);
        return;
    }
    if (tab === "pending") {
        table.querySelector("thead").innerHTML = `<tr><th>ID</th><th>发件邮箱</th><th>主题</th><th>收信时间</th><th>关联专家</th><th>原因</th><th>操作</th></tr>`;
        table.querySelector("tbody").innerHTML = rows.map((r) => `
            <tr><td>${escapeHtml(r.id)}</td><td>${escapeHtml(r.fromEmail)}</td><td>${escapeHtml(r.subject || "-")}</td>
            <td>${escapeHtml(r.receivedAt || "-")}</td><td>${escapeHtml(r.expertName || "-")}</td><td>${escapeHtml(r.reasonType || r.processReason)}</td>
            <td><button class="button" data-action="view-unmatched" data-id="${escapeHtml(r.id)}">处理</button></td></tr>
        `).join("") || renderEmpty(7);
        return;
    }
    table.querySelector("thead").innerHTML = `<tr><th>时间</th><th>专家</th><th>触发</th><th>状态</th><th>层级</th><th>来源来信</th><th>错误</th><th>操作</th></tr>`;
    table.querySelector("tbody").innerHTML = rows.map((r) => `
        <tr><td>${escapeHtml(r.createdAt || "-")}</td><td>${monitoringContactCell(r)}</td><td>${escapeHtml(triggeredByLabels[r.triggeredBy] || r.triggeredBy)}</td>
        <td>${badge(promotionStatusLabels[r.promotionStatus] || r.promotionStatus, r.promotionStatus === "FAILED" ? "error" : "ok")}</td>
        <td>${escapeHtml(r.fromLevel)} → ${escapeHtml(r.toLevel)}</td><td>${escapeHtml(r.sourceInboundId || "-")}</td><td>${escapeHtml(r.errorMessage || "-")}</td>
        <td>${r.promotionStatus === "FAILED" ? `<button class="button" data-action="retry-promotion" data-id="${escapeHtml(r.promotionId)}">重试</button>` : ""}</td></tr>
    `).join("") || renderEmpty(8);
}

function monitoringContactCell(row) {
    const label = row.expertName || row.orcidId || row.expertEmail || row.expertContactId || "-";
    if (!row.expertContactId) return escapeHtml(label);
    return `<a href="javascript:void 0" data-action="open-monitoring-contact" data-id="${escapeHtml(row.expertContactId)}">${escapeHtml(label)}</a>`;
}

function renderMonitoringPagination() {
    const total = state.monitoring.totalCount || 0;
    const page = state.monitoring.page;
    const maxPage = Math.max(0, Math.ceil(total / state.monitoring.pageSize) - 1);
    $("#monitoringPagination").innerHTML = `
        <span class="muted">共 ${escapeHtml(total)} 条，第 ${escapeHtml(page + 1)} / ${escapeHtml(maxPage + 1)} 页</span>
        <button class="button secondary" data-action="monitoring-prev" ${page <= 0 ? "disabled" : ""}>上一页</button>
        <button class="button secondary" data-action="monitoring-next" ${page >= maxPage ? "disabled" : ""}>下一页</button>
    `;
}

function renderMonitoringLastRefreshed() {
    $("#monitoringLastRefreshed").textContent = state.monitoring.lastRefreshedAt
        ? `最近刷新 ${state.monitoring.lastRefreshedAt.toLocaleTimeString()}`
        : "";
}

function scheduleMonitoringAutoRefresh() {
    if (state.monitoring.autoRefreshTimer) clearTimeout(state.monitoring.autoRefreshTimer);
    if (state.view !== "monitoring") return;
    state.monitoring.autoRefreshTimer = setTimeout(async () => {
        try {
            const params = new URLSearchParams();
            if (state.monitoring.date) params.set("date", state.monitoring.date);
            const [summary, senderHealth] = await Promise.all([
                api(`/api/mail-monitoring/summary?${params}`),
                api(`/api/mail-monitoring/sender-accounts?${params}`)
            ]);
            state.monitoring.summary = summary;
            state.monitoring.senderHealth = senderHealth || [];
            renderMonitoringCards();
            renderMonitoringSenderHealth();
            state.monitoring.lastRefreshedAt = new Date();
            renderMonitoringLastRefreshed();
        } catch (error) {
            showStatus(error.message, "error");
        } finally {
            scheduleMonitoringAutoRefresh();
        }
    }, 60000);
}

function bindMonitoringEvents() {
    $("#monitoringRefreshBtn").addEventListener("click", () => loadMonitoring().catch((e) => showStatus(e.message, "error")));
    $("#monitoringDate").addEventListener("change", (event) => {
        state.monitoring.date = event.target.value || null;
        state.monitoring.page = 0;
        loadMonitoring().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringSenderAccount").addEventListener("change", () => {
        state.monitoring.page = 0;
        loadMonitoringSubTab().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringSubTabs").addEventListener("click", (event) => {
        const tab = event.target.closest("[data-subtab]");
        if (!tab) return;
        state.monitoring.subTab = tab.dataset.subtab;
        state.monitoring.page = 0;
        $$("#monitoringSubTabs .tab").forEach((item) => item.classList.toggle("active", item === tab));
        loadMonitoringSubTab().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringPagination").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        if (button.dataset.action === "monitoring-prev") state.monitoring.page = Math.max(0, state.monitoring.page - 1);
        if (button.dataset.action === "monitoring-next") state.monitoring.page += 1;
        loadMonitoringSubTab().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringActivityTable").addEventListener("click", async (event) => {
        const target = event.target.closest("[data-action]");
        if (!target) return;
        if (target.dataset.action === "open-monitoring-contact") {
            state.selectedExpertOrcid = null;
            setView("contacts");
            await loadContactDetail(Number(target.dataset.id));
        }
        if (target.dataset.action === "view-unmatched") {
            setView("unmatched");
            await showUnmatchedDetail(target.dataset.id);
        }
        if (target.dataset.action === "retry-promotion") {
            await api(`/api/mail-monitoring/promotions/${target.dataset.id}/retry`, { method: "POST" });
            showStatus("晋级重试已提交");
            await loadMonitoringSubTab();
        }
    });
}

function bindEvents() {
    $$(".nav-tab").forEach((tab) => tab.addEventListener("click", () => setView(tab.dataset.view)));
    $("#refreshBtn").addEventListener("click", refreshCurrentView);
    bindMonitoringEvents();
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
    $("#newQaRuleBtn").addEventListener("click", () => fillQaRuleForm(null));
    $("#clearQaRuleBtn").addEventListener("click", hideQaRuleEditor);
    $("#qaRuleModalCloseBtn").addEventListener("click", hideQaRuleEditor);
    $("#qaRuleModalBackdrop").addEventListener("click", hideQaRuleEditor);
    $("#qaCategoryFilter").addEventListener("change", renderQaRulesTable);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#qaRuleModal").hidden) {
            hideQaRuleEditor();
        }
    });
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
    document.addEventListener("submit", (event) => {
        const form = event.target.closest("#meetingScheduleForm");
        if (form) {
            event.preventDefault();
            confirmMeetingSchedule(form).catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#loadUnmatchedBtn").addEventListener("click", loadUnmatched);
    $("#unmatchedFilterEmail").addEventListener("input", () => {
        state.unmatchedPageOffset = 0;
        loadUnmatched().catch((e) => showStatus(e.message, "error"));
    });
    $("#unmatchedFilterSubject").addEventListener("input", () => {
        state.unmatchedPageOffset = 0;
        loadUnmatched().catch((e) => showStatus(e.message, "error"));
    });
    $("#unmatchedFilterReasonType").addEventListener("change", () => {
        state.unmatchedPageOffset = 0;
        loadUnmatched().catch((e) => showStatus(e.message, "error"));
    });
    $("#unmatchedPageSize").addEventListener("change", () => {
        state.unmatchedPageOffset = 0;
        loadUnmatched().catch((e) => showStatus(e.message, "error"));
    });
    $("#expertIndexLevel").addEventListener("change", loadContacts);
    $("#expertIndexSize").addEventListener("change", loadContacts);
    $("#contactNeedsAttentionFilter").addEventListener("change", loadContacts);
    $("#contactStatusFilter").addEventListener("change", loadContacts);

    $("#unmatchedTable").addEventListener("click", (event) => {
        const button = event.target.closest("[data-action]");
        if (button) handleUnmatchedAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#unmatchedDetailPanel").addEventListener("click", (event) => {
        const button = event.target.closest("[data-action]");
        if (button) handleUnmatchedAction(button).catch((error) => showStatus(error.message, "error"));
    });
    document.addEventListener("click", async (event) => {
        const element = event.target.closest("[data-action]");
        if (!element) return;
        const action = element.dataset.action;
        if (action === "goto-manual-queue") {
            event.preventDefault();
            setView("unmatched");
            return;
        }
        if (action === "add-alias") {
            const contactId = element.dataset.contactId;
            const email = $("#newAliasEmail").value.trim();
            if (!email) { showStatus("请输入邮箱地址", "error"); return; }
            api(`/api/expert-contacts/${contactId}/email-aliases`, {
                method: "POST",
                body: JSON.stringify({ email, source: "MANUAL_ADD" })
            }).then(() => {
                showStatus("别名已添加");
                loadContactDetail(contactId);
            }).catch((e) => showStatus(e.message, "error"));
        }
        if (action === "delete-alias") {
            const contactId = element.dataset.contactId;
            const aliasId = element.dataset.aliasId;
            const confirmDelete = await openActionDialog("confirm", { message: "确定移除该别名？" });
            if (!confirmDelete) return;
            api(`/api/expert-contacts/${contactId}/email-aliases/${aliasId}`, {
                method: "DELETE"
            }).then(() => {
                showStatus("别名已移除");
                loadContactDetail(contactId);
            }).catch((e) => showStatus(e.message, "error"));
        }
    });

    $("#contactStatusFilter").innerHTML = optionsFromLabels(conversationStatusLabels, true, "全部状态");

    updateUnmatchedBadge();
}

const ACTION_DIALOG_SCHEMAS = {
    "mark-unmatched-resolved": {
        title: "标记为已处理",
        fields: [
            { name: "resolvedBy", label: "操作人姓名", type: "text", required: true },
            { name: "note", label: "处理备注", type: "textarea", required: false }
        ]
    },
    "bind-unmatched-contact": {
        title: "绑定未匹配来信",
        fields: [
            { name: "resolvedBy", label: "操作人姓名", type: "text", required: true },
            { name: "promoteToApplication", label: "同时加入有效层", type: "checkbox", required: false }
        ]
    },
    "switch-to-manual": {
        title: "切换为人工回复",
        fields: [
            {
                name: "reason",
                label: "转人工原因",
                type: "select",
                value: "OPERATOR_SWITCH_TO_MANUAL",
                options: [
                    { value: "OPERATOR_SWITCH_TO_MANUAL", label: "运营手动转人工" },
                    { value: "UNCLEAR_INTENT", label: "意图模糊" },
                    { value: "NOT_INTERESTED", label: "专家不感兴趣" },
                    { value: "QA_NO_MATCH", label: "QA 未命中" }
                ],
                required: true
            },
            { name: "note", label: "备注信息", type: "textarea", required: false }
        ]
    },
    "switch-to-auto": {
        title: "切换为自动回复",
        fields: [
            { name: "note", label: "备注信息", type: "textarea", required: false }
        ]
    },
    "initiate-meeting-schedule": {
        title: "发起会议排期",
        fields: [
            { name: "availableText", label: "专家可沟通时间说明", type: "textarea", required: false, placeholder: "留空则由手动填写" }
        ]
    },
    "confirm": {
        title: "确认操作",
        fields: [
            { name: "message", label: "", type: "html", required: false }
        ]
    }
};

function openActionDialog(type, options = {}) {
    return new Promise((resolve) => {
        const dialog = document.getElementById("actionDialog");
        const form = document.getElementById("actionDialogForm");
        const titleEl = document.getElementById("actionDialogTitle");
        const bodyEl = document.getElementById("actionDialogBody");

        const schema = ACTION_DIALOG_SCHEMAS[type];
        if (!schema) {
            console.error("Unknown dialog type:", type);
            resolve(null);
            return;
        }

        titleEl.textContent = schema.title;

        // Render fields
        let html = "";
        const fields = schema.fields;
        fields.forEach(field => {
            if (field.type === "html") {
                html += `<div>${options.message || ''}</div>`;
            } else if (field.type === "checkbox") {
                html += `
                    <div class="form-group" style="margin-bottom: 12px; display: flex; align-items: center;">
                        <input type="checkbox" id="dialog_${field.name}" name="${field.name}" ${field.value ? 'checked' : ''} style="margin-right: 8px;">
                        <label for="dialog_${field.name}">${escapeHtml(field.label)}</label>
                    </div>
                `;
            } else if (field.type === "select") {
                html += `
                    <div class="form-group" style="margin-bottom: 12px; display: flex; flex-direction: column;">
                        <label style="font-weight: bold; margin-bottom: 4px;">${escapeHtml(field.label)}</label>
                        <select id="dialog_${field.name}" name="${field.name}" class="input" style="width: 100%; box-sizing: border-box;">
                            ${field.options.map(opt => `
                                <option value="${escapeHtml(opt.value)}" ${opt.value === field.value ? 'selected' : ''}>
                                    ${escapeHtml(opt.label)}
                                </option>
                            `).join("")}
                        </select>
                    </div>
                `;
            } else if (field.type === "textarea") {
                html += `
                    <div class="form-group" style="margin-bottom: 12px; display: flex; flex-direction: column;">
                        <label style="font-weight: bold; margin-bottom: 4px;">${escapeHtml(field.label)}</label>
                        <textarea id="dialog_${field.name}" name="${field.name}" rows="3" class="input" style="width: 100%; box-sizing: border-box;">${escapeHtml(field.value || '')}</textarea>
                    </div>
                `;
            } else {
                html += `
                    <div class="form-group" style="margin-bottom: 12px; display: flex; flex-direction: column;">
                        <label style="font-weight: bold; margin-bottom: 4px;">${escapeHtml(field.label)}</label>
                        <input type="${field.type}" id="dialog_${field.name}" name="${field.name}" value="${escapeHtml(field.value || '')}" placeholder="${escapeHtml(field.placeholder || '')}" class="input" style="width: 100%; box-sizing: border-box;" ${field.required ? 'required' : ''}>
                    </div>
                `;
            }
        });
        bodyEl.innerHTML = html;

        const handleCancel = () => {
            cleanup();
            resolve(null);
        };

        const handleSubmit = (e) => {
            e.preventDefault();
            const result = {};
            fields.forEach(field => {
                const el = document.getElementById(`dialog_${field.name}`);
                if (!el) return;
                if (field.type === "checkbox") {
                    result[field.name] = el.checked;
                } else {
                    result[field.name] = el.value;
                }
            });
            cleanup();
            resolve(result);
        };

        const cleanup = () => {
            form.removeEventListener("submit", handleSubmit);
            const cancelBtn = form.querySelector("[data-action='action-dialog-cancel']");
            cancelBtn.removeEventListener("click", handleCancel);
            dialog.close();
        };

        const cancelBtn = form.querySelector("[data-action='action-dialog-cancel']");
        cancelBtn.addEventListener("click", handleCancel);
        form.addEventListener("submit", handleSubmit);

        dialog.showModal();
    });
}

bindEvents();
refreshCurrentView();
