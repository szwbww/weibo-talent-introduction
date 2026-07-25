function monitoringToday() {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(new Date());
}

const state = {
    lastEmailProvidersLevel: null,
    view: "accounts",
    accounts: [],
    categories: [],
    qaRules: [],
    qaCoverageKeys: [],
    composeTemplates: [],
    mailTemplatesSubTab: "qa",
    selectedComposeTemplateId: null,
    composeTemplatePreviewExperts: [],
    composeTemplatePreviewAccounts: [],
    composeTemplatePreviewOptionsLoaded: false,
    suppressions: [],
    suppressionsPage: 0,
    suppressionsTotal: 0,
    suppressionKeyword: "",
    contacts: [],
    contactsPage: 0,
    contactsTotalHits: 0,
    mailSendOptions: [],
    selectedAccount: null,
    accountEditorMode: null,
    currentEditAccount: null,
    selectedExpertOrcid: null,
    selectedRuleId: null,
    unmatchedRecords: [],
    unmatchedFiltered: [],
    monitoring: {
        date: monitoringToday(),
        summary: null,
        subTab: "introductions",
        page: 0,
        pageSize: 20,
        rows: [],
        totalCount: 0,
        senderHealth: [],
        providerDistribution: [],
        regionDistribution: [],
        reputationHistory: [],
        reputationDomains: [],
        reputationDomain: "",
        reputationDays: 30,
        lastRefreshedAt: null,
        autoRefreshTimer: null
    },
    mailbox: {
        items: [],
        groups: [],
        viewMode: "MAIL",
        page: 0,
        totalCount: 0,
        pageSize: 20,
        accountsLoaded: false,
        dateDefaultsApplied: false,
        onlyPending: false,
        tagFilter: "",
        detailContext: null
    },
    inboundSummary: {
        from: "",
        to: "",
        page: 0,
        pageSize: 20,
        activeTagKey: "",
        search: "",
        mails: [],
        total: 0,
        selectedId: null,
        stats: null,
        options: [],
        thread: null,
        datesInitialized: false,
        groupByExpert: true,
        tagEditInboundId: null
    },
    qaAudit: {
        from: "",
        to: "",
        report: null
    },
    replySnippets: [],
    selectedReplySnippetId: null,
    aiTraining: {
        activeTab: "simulate",
        qaPage: 0,
        qaSize: 20,
        qaTotal: 0,
        qaSource: "",
        qaItems: [],
        editingQaId: null,
        dialogueItems: [],
        promptConfig: null,
        promptIsCustom: false,
        expertTagOptions: [],
        inboundTagOptions: [],
        selectedExpertTag: "",
        selectedInboundTagKey: "",
        simulateMails: [],
        simulateMailsTotal: 0,
        simulateMailsPage: 0,
        simulateMailsSize: 20,
        selectedSimulateMailContactId: null,
        selectedSimulateMailRecordId: null,
        selectedSimulateMail: null,
        simulateRequestSeq: 0,
        simulateResult: null,
        simulateModel: "DEEPSEEK_V4_FLASH"
    },
    variableMeta: null,
    variableMetaLoaded: false,
    previewDrawer: {
        targetId: null,
        contactId: null,
        orcidId: null,
        level: "CANDIDATE",
        mode: "SATISFY_ALL",
        expertEmail: null,
        matchCount: null,
        totalCount: null,
        variantIndex: 0,
        variantPoolSize: 1
    }
};

const composedReplyState = {
    recordId: null,
    contactId: null,
    contactOrcid: null,
    suggest: null,
    selectedFactIds: [],
    evaluation: null,
    confirmedEvaluation: null,
    evaluationPending: false,
    lockedFactIds: null,
    draft: null,
    evaluateTimer: null,
    evaluateSeq: 0
};

function sameFactIdSet(left, right) {
    const a = [...(left || [])].sort((x, y) => x - y);
    const b = [...(right || [])].sort((x, y) => x - y);
    if (a.length !== b.length) {
        return false;
    }
    return a.every((value, index) => value === b[index]);
}

function confirmedCanonicalFactIds() {
    if (composedReplyState.evaluationPending || !composedReplyState.confirmedEvaluation) {
        return null;
    }
    const canonical = composedReplyState.confirmedEvaluation.canonicalFactIds || [];
    if (!sameFactIdSet(canonical, composedReplyState.selectedFactIds)) {
        return null;
    }
    return [...canonical];
}

function markComposedEvaluationPending() {
    composedReplyState.evaluationPending = true;
    composedReplyState.confirmedEvaluation = null;
    composedReplyState.evaluation = null;
    clearComposedDraftSession();
}

let manualReplyQaContext = null;

const preflightState = {
    timerId: null,
    seq: 0,
    loading: false
};

function resetPreflightState() {
    preflightState.seq += 1;
    if (preflightState.timerId) {
        clearTimeout(preflightState.timerId);
        preflightState.timerId = null;
    }
    preflightState.loading = false;
    const el = $("#manualReplyPreflight");
    if (el) {
        el.hidden = true;
        el.innerHTML = "";
    }
}

const aiReplyState = {
    recordId: null,
    turns: [],
    lastDraftTemplate: "",
    lastRenderedDraft: "",
    /** Matched QA subset for send-path audit (composed-reply vs manual-rich-reply), not prompt rule set. */
    lastQaRuleIds: [],
    /** Locked after first turn: QA_MATCHED | FREE_FORM | QA_GROUNDED */
    mode: null,
    /** Whether first turn has completed (locks mode and qaRuleIds for continuation). */
    firstTurnDone: false,
    drafts: {},
    nextDraftId: 0,
    /** { rawTemplate, renderedBaseline, renderedBaselineHtml, recordId } — cleared on reset / mail switch / send. */
    adoptContext: null,
    requestSeq: 0,
    inFlight: false,
    selectedModel: "DEEPSEEK_V4_FLASH",
    attemptTimeoutMode: "30",
    attemptTimeoutSeconds: 30,
    attemptCustomSeconds: 30,
    totalTimeoutMode: "auto",
    totalTimeoutSeconds: 300,
    totalCustomSeconds: 300,
    activeGeneration: null,
    latestProgress: null,
    lastProgressSeq: -1,
    progressReceivedAt: 0,
    progressTimerId: null
};

const AI_REPLY_MODEL_LABELS = {
    DEEPSEEK_V4_FLASH: "DeepSeek V4 Flash",
    DEEPSEEK_V4_PRO: "DeepSeek V4 Pro"
};

const AI_REPLY_PROGRESS_PHASE_LABELS = {
    QUEUED: "排队等待生成",
    PREPARING: "正在准备生成上下文",
    CALLING: "正在调用 DeepSeek",
    VALIDATING: "正在校验结构与事实",
    REPAIRING: "正在修复未通过的输出",
    FINALIZING: "正在生成最终结果"
};

const AI_REPLY_PROVIDER_ACTIVITY_LABELS = {
    IDLE: "等待服务端活动",
    WAITING: "等待 DeepSeek 数据",
    REASONING: "DeepSeek 思考中",
    WRITING: "DeepSeek 正在输出回复"
};

function integerSeconds(value) {
    const number = Number(value);
    return Number.isInteger(number) ? number : null;
}

function resolveAiReplyTimeoutSelection() {
    const attemptMode = aiReplyState.attemptTimeoutMode;
    const attempt = attemptMode === "custom"
        ? integerSeconds(aiReplyState.attemptCustomSeconds)
        : integerSeconds(attemptMode);
    if (attempt == null || attempt < 10 || attempt > 600) {
        throw new Error("自定义单次 TTL 需为 10–600 的整数秒");
    }
    const total = aiReplyState.totalTimeoutMode === "auto"
        ? attempt * 10
        : aiReplyState.totalTimeoutMode === "custom"
            ? integerSeconds(aiReplyState.totalCustomSeconds)
            : integerSeconds(aiReplyState.totalTimeoutMode);
    if (total != null && total < attempt) throw new Error("总 TTL 必须大于或等于单次 TTL");
    if (total == null || total > 7200) {
        throw new Error("自定义总 TTL 需为单次 TTL 至 7200 秒的整数");
    }
    aiReplyState.attemptTimeoutSeconds = attempt;
    aiReplyState.totalTimeoutSeconds = total;
    return {
        attemptTimeoutSeconds: attempt,
        totalTimeoutSeconds: total,
        totalPayload: aiReplyState.totalTimeoutMode === "auto" ? null : total
    };
}

function syncAiReplyTimeoutControls() {
    const attemptSelect = $("#trustReplyAttemptTimeout");
    const totalSelect = $("#trustReplyTotalTimeout");
    const attemptCustom = $("#trustReplyAttemptTimeoutCustom");
    const totalCustom = $("#trustReplyTotalTimeoutCustom");
    const attemptWrap = $("#trustReplyAttemptTimeoutCustomWrap");
    const totalWrap = $("#trustReplyTotalTimeoutCustomWrap");
    if (attemptSelect) attemptSelect.value = aiReplyState.attemptTimeoutMode;
    if (totalSelect) totalSelect.value = aiReplyState.totalTimeoutMode;
    if (attemptCustom) attemptCustom.value = aiReplyState.attemptCustomSeconds;
    if (totalCustom) totalCustom.value = aiReplyState.totalCustomSeconds;
    if (attemptWrap) attemptWrap.hidden = aiReplyState.attemptTimeoutMode !== "custom";
    if (totalWrap) totalWrap.hidden = aiReplyState.totalTimeoutMode !== "custom";
    const auto = totalSelect?.querySelector("option[value='auto']");
    const attemptForLabel = aiReplyState.attemptTimeoutMode === "custom"
        ? (integerSeconds(aiReplyState.attemptCustomSeconds) || 30)
        : (integerSeconds(aiReplyState.attemptTimeoutMode) || 30);
    if (auto) auto.textContent = `自动（${attemptForLabel * 10} 秒）`;
}

function createAiReplyGenerationId() {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
    const bytes = new Uint8Array(16);
    if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function parseAiReplySseFrames(buffer, flush = false) {
    const events = [];
    let remainder = String(buffer || "");
    const parts = remainder.split(/\r?\n\r?\n/);
    remainder = flush ? "" : (parts.pop() || "");
    parts.forEach((frame) => {
        let event = "message";
        const data = [];
        frame.split(/\r?\n/).forEach((line) => {
            if (line.startsWith("event:")) event = line.slice(6).trim() || "message";
            if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
        });
        if (data.length) {
            try {
                events.push({ event, data: JSON.parse(data.join("\n")) });
            } catch {
                events.push({ event, data: null, invalid: true });
            }
        }
    });
    return { events, remainder };
}

function normalizeAiReplyProgressSnapshot(snapshot) {
    if (!snapshot || typeof snapshot !== "object") return null;
    const phases = Object.keys(AI_REPLY_PROGRESS_PHASE_LABELS);
    const activities = Object.keys(AI_REPLY_PROVIDER_ACTIVITY_LABELS);
    const numericFields = [
        "progressSeq", "providerCallIndex", "attemptElapsedSeconds", "attemptTimeoutSeconds",
        "totalElapsedSeconds", "totalTimeoutSeconds", "providerEventCount", "contentChars",
        "secondsSinceProviderActivity"
    ];
    if (typeof snapshot.generationId !== "string" || !phases.includes(snapshot.phase)
        || !activities.includes(snapshot.providerActivity)) return null;
    const normalized = { ...snapshot };
    for (const field of numericFields) {
        const value = integerSeconds(snapshot[field]);
        if (value == null || value < 0 || value > 2147483647) return null;
        normalized[field] = value;
    }
    if (normalized.totalTimeoutSeconds <= 0 || normalized.attemptTimeoutSeconds <= 0
        || normalized.providerCallIndex > 2147483647) return null;
    normalized.attemptElapsedSeconds = Math.min(normalized.attemptElapsedSeconds, normalized.attemptTimeoutSeconds);
    normalized.totalElapsedSeconds = Math.min(normalized.totalElapsedSeconds, normalized.totalTimeoutSeconds);
    return normalized;
}

function acceptAiReplyProgressSnapshot(snapshot) {
    const active = aiReplyState.activeGeneration;
    const normalized = normalizeAiReplyProgressSnapshot(snapshot);
    if (!active || !normalized || normalized.generationId !== active.generationId
        || normalized.progressSeq <= aiReplyState.lastProgressSeq) return false;
    aiReplyState.latestProgress = normalized;
    aiReplyState.lastProgressSeq = normalized.progressSeq;
    aiReplyState.progressReceivedAt = performance.now();
    renderAiReplyProgress(normalized);
    return true;
}

function renderAiReplyProgress(snapshot) {
    const overlay = $(".compose-draft.ai-chat-panel .ai-reply-loading-overlay");
    if (!overlay || !snapshot) return;
    const phase = overlay.querySelector(".ai-reply-progress-phase");
    const detail = overlay.querySelector(".ai-reply-progress-detail");
    const activity = overlay.querySelector(".ai-reply-progress-activity");
    const track = overlay.querySelector(".ai-reply-progress-track");
    if (phase) phase.textContent = AI_REPLY_PROGRESS_PHASE_LABELS[snapshot.phase];
    const call = snapshot.providerCallIndex === 0
        ? `尚未调用模型 · 总计 ${snapshot.totalElapsedSeconds}/${snapshot.totalTimeoutSeconds} 秒`
        : `第 ${snapshot.providerCallIndex} 次模型调用 · 本次 ${snapshot.attemptElapsedSeconds}/${snapshot.attemptTimeoutSeconds} 秒 · 总计 ${snapshot.totalElapsedSeconds}/${snapshot.totalTimeoutSeconds} 秒`;
    if (detail) detail.textContent = call;
    if (activity) {
        const chars = snapshot.providerActivity === "WRITING" ? ` · 已接收 ${snapshot.contentChars} 字符` : "";
        activity.textContent = `${AI_REPLY_PROVIDER_ACTIVITY_LABELS[snapshot.providerActivity]} · 最近活动 ${snapshot.secondsSinceProviderActivity} 秒前 · 已接收 ${snapshot.providerEventCount} 个流事件${chars}`;
    }
    const value = Math.max(0, Math.min(100, snapshot.totalElapsedSeconds / snapshot.totalTimeoutSeconds * 100));
    if (track) {
        track.value = value;
        track.setAttribute("aria-valuenow", String(value));
        track.title = `已使用总 TTL ${snapshot.totalElapsedSeconds}/${snapshot.totalTimeoutSeconds} 秒`;
    }
}

function updateAiReplyLoadingMessage(message) {
    const text = $(".compose-draft.ai-chat-panel .ai-reply-loading-text");
    if (text) text.textContent = message;
}

function startAiReplyProgressTicker() {
    stopAiReplyProgressTicker();
    aiReplyState.progressTimerId = setInterval(() => {
        const snapshot = aiReplyState.latestProgress;
        if (!snapshot || !aiReplyState.progressReceivedAt) return;
        const elapsed = Math.max(0, Math.floor((performance.now() - aiReplyState.progressReceivedAt) / 1000));
        const next = { ...snapshot, totalElapsedSeconds: Math.min(snapshot.totalTimeoutSeconds, snapshot.totalElapsedSeconds + elapsed) };
        if (snapshot.providerActivity !== "IDLE") {
            next.attemptElapsedSeconds = Math.min(snapshot.attemptTimeoutSeconds, snapshot.attemptElapsedSeconds + elapsed);
            next.secondsSinceProviderActivity = Math.min(2147483647, snapshot.secondsSinceProviderActivity + elapsed);
        }
        renderAiReplyProgress(next);
    }, 1000);
}

function stopAiReplyProgressTicker() {
    if (aiReplyState.progressTimerId) clearInterval(aiReplyState.progressTimerId);
    aiReplyState.progressTimerId = null;
}

async function requestAiReplyCancellation(recordId, generationId) {
    if (!recordId || !generationId) return { status: "NOT_ACTIVE" };
    return api(`/api/mail/unmatched-inbound/${recordId}/ai-reply/generations/${generationId}/cancel`, { method: "POST" });
}

async function cancelActiveAiReplyGeneration({ abort = true } = {}) {
    const active = aiReplyState.activeGeneration;
    if (!active) return { status: "NOT_ACTIVE" };
    let result;
    try {
        result = await requestAiReplyCancellation(active.recordId, active.generationId);
    } catch {
        result = { status: "NOT_ACTIVE" };
    }
    if (result.status === "TOO_LATE") return result;
    if (abort) active.controller?.abort();
    if (aiReplyState.activeGeneration?.generationId === active.generationId) aiReplyState.activeGeneration = null;
    stopAiReplyProgressTicker();
    aiReplyState.latestProgress = null;
    return result;
}

async function postAiReplySse(recordId, body, handlers = {}) {
    const controller = new AbortController();
    if (aiReplyState.activeGeneration?.generationId === body.generationId) {
        aiReplyState.activeGeneration.controller = controller;
    }
    const total = Number(body._resolvedTotalTimeoutSeconds || 300);
    const timer = setTimeout(() => controller.abort(), (total + 35) * 1000);
    const response = await fetch(`${contextPath}/api/mail/unmatched-inbound/${recordId}/ai-reply/turn-stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify(Object.fromEntries(Object.entries(body).filter(([key]) => !key.startsWith("_")))),
        signal: controller.signal
    });
    await handleAuthResponse(response);
    if (!response.ok || !response.body) throw new Error(`SSE ${response.status} ${response.statusText}`);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let terminal = false;
    try {
        while (true) {
            const chunk = await reader.read();
            buffer += decoder.decode(chunk.value || new Uint8Array(), { stream: !chunk.done });
            const parsed = parseAiReplySseFrames(buffer, chunk.done);
            buffer = parsed.remainder;
            for (const item of parsed.events) {
                if (item.invalid) throw new Error("SSE 数据格式无效");
                if (item.event === "ready") {
                    handlers.onReady?.(item.data);
                    acceptAiReplyProgressSnapshot(item.data?.progress);
                } else if (item.event === "progress") {
                    acceptAiReplyProgressSnapshot(item.data);
                    handlers.onProgress?.(item.data);
                } else if (item.event === "heartbeat") {
                    acceptAiReplyProgressSnapshot(item.data?.progress);
                } else if (["result", "cancelled", "error"].includes(item.event)) {
                    terminal = true;
                    handlers.onTerminal?.(item.event, item.data);
                }
            }
            if (chunk.done) break;
        }
        if (!terminal) throw new Error("SSE 在收到终态前断开");
    } finally {
        clearTimeout(timer);
        reader.cancel().catch(() => {});
    }
}

function aiReplyModelLabel(model) {
    return AI_REPLY_MODEL_LABELS[model] || AI_REPLY_MODEL_LABELS.DEEPSEEK_V4_FLASH;
}

function readAiReplyModelSelection(selectId, fallback) {
    const select = $(selectId);
    const value = select?.value || fallback || "DEEPSEEK_V4_FLASH";
    return AI_REPLY_MODEL_LABELS[value] ? value : "DEEPSEEK_V4_FLASH";
}

const contextPath = (() => {
    const firstSegment = window.location.pathname.split("/").filter(Boolean)[0];
    return firstSegment ? `/${firstSegment}` : "";
})();

const viewMeta = {
    monitoring: ["邮件监控", "当日活动概览、自动回复全链路、发件账号健康。"],
    accounts: ["邮箱账号", "维护发送账号、权重、限额和连通性。"],
    "mail-templates": ["邮件模板", "统一管理 QA 规则、回复片段与邮件模板。"],
    suppressions: ["退订名单", "查看和管理退订抑制邮箱，手动加入或移除。"],
    contacts: ["专家联系", "查看联系状态、邮件时间线和人工处理。"],
    mailbox: ["收发件箱", "查看所有已激活邮箱账号的收发记录，含待处理来信与标签筛选。"],
    "inbound-summary": ["来信汇总", "按标签汇总来信、查看往来记录与标签统计。"],
    "ai-training": ["AI 回复训练", "导入提炼 QA、配置提示词与约束，用历史邮件模拟 AI 回复效果。"],
    tasks: ["任务记录", "查看定时任务、队列消费和失败记录。"]
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
    PARTIAL_SUCCESS: "部分成功",
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
    OPERATOR: "人工",
    MANUAL: "批量发送"
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

const operatorStatusLabels = {
    NOT_CONTACTED: "未联系",
    CONTACTED: "已联系",
    REPLIED: "已回复",
    MATERIALS_RECEIVED: "已回复材料",
    INVITED: "已邀约",
    COMPLETED: "已完成"
};

const operatorStatusOptions = [
    ["NOT_CONTACTED", "未联系"],
    ["CONTACTED", "已联系"],
    ["REPLIED", "已回复"],
    ["MATERIALS_RECEIVED", "已回复材料"],
    ["INVITED", "已邀约"],
    ["COMPLETED", "已完成"]
];

const indexLevelOptions = [
    ["RAW", "原始"],
    ["CANDIDATE", "筛选"],
    ["APPLICATION", "有效"]
];

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

function getConfiguredOperatorName() {
    return window.localStorage.getItem("operatorName")?.trim() || "";
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

const shownErrors = new Set();
const taskButtonOriginalTexts = {
    checkRepliesBtn: "检查回复",
    discoverBtn: "发现专家",
    bulkOutreachBtn: "批量发送"
};

const taskButtonMapping = {
    EXPERT_REVALIDATION: { label: "重新验证", btnId: "discoverBtn" },
    RAW_PROMOTION_SCAN: { label: "快速晋升（扫描 RAW）", btnId: "discoverBtn" },
    EXPERT_DISCOVERY: { label: "深度发现（外部数据源）", btnId: "discoverBtn" },
    EXPERT_ENRICHMENT: { label: "补充学术数据（OpenAlex）", btnId: "discoverBtn" },
    MANUAL_INITIAL_OUTREACH: { label: "批量发送邮件", btnId: "bulkOutreachBtn" },
    CHECK_REPLIES: { label: "检查回复", btnId: "checkRepliesBtn" }
};

function restoreTaskButton(btnId) {
    const btn = $(`#${btnId}`);
    if (btn) {
        btn.disabled = false;
        btn.innerHTML = escapeHtml(taskButtonOriginalTexts[btnId] || btn.textContent);
    }
}

function showTaskErrorLog(message) {
    const logPanel = $("#taskErrorLog");
    const logContent = $("#taskErrorLogContent");
    logPanel.hidden = false;
    const timestamp = new Date().toLocaleTimeString();
    logContent.textContent += `[${timestamp}] ${message}\n`;
}

function clearTaskErrorLog() {
    const logPanel = $("#taskErrorLog");
    const logContent = $("#taskErrorLogContent");
    logPanel.hidden = true;
    logContent.textContent = "";
}

function hideProgressBar() {
    const bar = $("#taskProgressBar");
    setTimeout(() => { bar.hidden = true; }, 2000);
}

async function resumeProgressPollingIfNeeded() {
    let firstRunningTask = null;
    for (const taskType of Object.keys(taskButtonMapping)) {
        try {
            const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
            await handleAuthResponse(response);
            if (response.status === 204 || !response.ok) continue;
            const progress = await response.json();
            if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
                const mapping = taskButtonMapping[taskType];
                if (mapping) setTaskButtonRunning(mapping.btnId);
                startTaskWatcher(taskType);
                if (!firstRunningTask) {
                    firstRunningTask = { taskType, mapping };
                }
            }
        } catch (e) { /* 静默 */ }
    }
    if (firstRunningTask && !currentTaskModal) {
        const { taskType, mapping } = firstRunningTask;
        openTaskModal(taskType, mapping.label, mapping.btnId, { knownActiveAtOpen: true });
    }
}

// taskModalGenerationSequence, createTaskModalContext, currentTaskModal,
// isProgressTerminal, getProgressStatusMeta, isExecutionTerminal, isCurrentTaskModal, bindTaskModalExecution,
// fetchJsonForCurrentTaskModal, fetchAndCacheBatchLogs are defined in task-modal-runtime.js

const TASK_WATCHER_INTERVAL_MS = 3000;
const TASK_WATCHER_LAUNCH_GRACE_MS = 30000;
const TASK_WATCHER_MAX_INITIAL_204 = 10;

const taskWatchers = {}; // taskType -> watcher state

function isCurrentTaskWatcher(taskType, watcher) {
    return watcher != null && taskWatchers[taskType] === watcher;
}

async function pollTaskWatcher(taskType) {
    const watcher = taskWatchers[taskType];
    if (!watcher) return;

    try {
        const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
        await handleAuthResponse(response);
        if (!isCurrentTaskWatcher(taskType, watcher)) return;

        if (response.status === 204) {
            watcher.noProgressCount += 1;

            const graceExpired =
                Date.now() - watcher.startedAt >= TASK_WATCHER_LAUNCH_GRACE_MS
                || watcher.noProgressCount >= TASK_WATCHER_MAX_INITIAL_204;

            if (watcher.awaitingLaunch && !watcher.observedActive && !graceExpired) {
                return;
            }

            stopTaskWatcher(taskType, true, watcher);
            return;
        }

        if (!response.ok) return;

        const progress = await response.json();
        if (!isCurrentTaskWatcher(taskType, watcher)) return;

        watcher.noProgressCount = 0;

        if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
            watcher.awaitingLaunch = false;
            watcher.observedActive = true;
            return;
        }

        if (isProgressTerminal(progress.status)) {
            stopTaskWatcher(taskType, true, watcher);
            const mapping = taskButtonMapping[taskType];
            const label = mapping?.label || taskType;
            const meta = getProgressStatusMeta(progress.status);
            notifyTaskCompletionOnce({
                taskType,
                executionId: progress.executionId,
                status: progress.status,
                message: `${label} ${meta.label}`,
                level: meta.level
            });
            if (taskType === "MANUAL_INITIAL_OUTREACH" || taskType === "RAW_PROMOTION_SCAN" || taskType === "EXPERT_DISCOVERY" || taskType === "EXPERT_REVALIDATION" || taskType === "EXPERT_ENRICHMENT") {
                loadContacts();
            }
        }
    } catch (e) {
        // 网络抖动：保留 watcher，下轮重试
    }
}

function startTaskWatcher(taskType, options = {}) {
    let watcher = taskWatchers[taskType];
    if (!watcher) {
        watcher = {
            intervalId: null,
            awaitingLaunch: !!options.awaitingLaunch,
            startedAt: Date.now(),
            noProgressCount: 0,
            observedActive: false
        };
        taskWatchers[taskType] = watcher;
        pollTaskWatcher(taskType);
        watcher.intervalId = setInterval(() => pollTaskWatcher(taskType), TASK_WATCHER_INTERVAL_MS);
    } else {
        if (options.awaitingLaunch) {
            watcher.awaitingLaunch = true;
            watcher.startedAt = Date.now();
            watcher.noProgressCount = 0;
        }
    }
}

function stopTaskWatcher(taskType, restoreButton, expectedWatcher) {
    const watcher = taskWatchers[taskType];
    if (expectedWatcher && watcher !== expectedWatcher) {
        return false;
    }

    if (watcher) {
        if (watcher.intervalId) {
            clearInterval(watcher.intervalId);
        }
        delete taskWatchers[taskType];
    }
    if (restoreButton) {
        const mapping = taskButtonMapping[taskType];
        if (mapping) restoreTaskButton(mapping.btnId);
    }
    return true;
}

function markTaskWatcherLaunchSucceeded(taskType, capturedGeneration) {
    if (!isCurrentTaskModal(taskType, capturedGeneration)) {
        const watcher = taskWatchers[taskType];
        if (watcher) {
            watcher.awaitingLaunch = false;
            watcher.observedActive = true;
            watcher.noProgressCount = 0;
        } else {
            startTaskWatcher(taskType);
        }
    }
}

async function progressStoreHasRunningTask() {
    for (const taskType of Object.keys(taskButtonMapping)) {
        if (await isTaskRunning(taskType)) return true;
    }
    return false;
}

async function isTaskRunning(taskType) {
    try {
        const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
        await handleAuthResponse(response);
        if (!response.ok) return false;
        const progress = await response.json();
        return progress.status === "RUNNING" || progress.status === "CANCELLING";
    } catch (e) { return false; }
}

function setTaskButtonRunning(btnId) {
    const btn = $(`#${btnId}`);
    if (!btn) return;
    btn.disabled = false;
    btn.innerHTML = `<span class="btn-running-indicator">执行中</span>`;
}

function openTaskModal(taskType, label, btnId, options = {}) {
    try {
        if (typeof options === "boolean") {
            options = { launchRequested: options };
        }
        const launchRequested = options.launchRequested === true;
        const knownActiveAtOpen = options.knownActiveAtOpen === true;

        // 停止旧轮询但不关闭弹窗；停止后台 watcher，由弹窗接管
        stopTaskModalPolling();
        stopTaskWatcher(taskType, false);

        const modal = $("#taskProgressModal");
        if (!modal) {
            console.error("#taskProgressModal not found!");
            return;
        }

        const titleEl = $("#taskModalTitle");
        const statusEl = $("#taskModalStatus");
        const percentEl = $("#taskModalPercent");
        const fillEl = $("#taskModalFill");
        const messageEl = $("#taskModalMessage");
        const cancelBtn = $("#taskModalCancelBtn");
        const errorsDiv = $("#taskModalErrors");
        const errorContent = $("#taskModalErrorContent");

        // Hide config section, show progress section
        const configSection = $("#taskModalConfigSection");
        if (configSection) configSection.hidden = true;

        const progressSection = $("#taskModalProgressSection");
        if (progressSection) progressSection.hidden = false;

        // Batch send control bar visibility: shown only for MANUAL_INITIAL_OUTREACH.
    if (taskType === "MANUAL_INITIAL_OUTREACH") {
        openBatchSendTaskModal();
        return;
    }
        // Batch send handled by new task console; nothing extra here for other types.
        stopBatchSendStatusPoll();

        modal.hidden = false;
        document.body.classList.add("modal-open");
        if (titleEl) titleEl.textContent = label;
        if (statusEl) {
            statusEl.textContent = "RUNNING";
            statusEl.className = "task-modal-status running";
        }
        if (percentEl) percentEl.textContent = "0%";
        if (fillEl) fillEl.style.width = "0%";
        if (messageEl) messageEl.textContent = "初始化中...";
        if (cancelBtn) {
            cancelBtn.disabled = false;
            cancelBtn.hidden = false;
            cancelBtn.textContent = taskType === "EXPERT_ENRICHMENT" ? "暂停" : "取消任务";
        }
        if (errorsDiv) errorsDiv.hidden = true;
        if (errorContent) errorContent.textContent = "";

        // 显示执行中（可点击重新打开弹窗）
        if (btnId) {
            setTaskButtonRunning(btnId);
        }

        currentTaskModal = createTaskModalContext(taskType, label, btnId, "PROGRESS");
        currentTaskModal.launchRequested = launchRequested;
        currentTaskModal.knownActiveAtOpen = knownActiveAtOpen;
        const capturedGeneration = currentTaskModal.generation;

    // 启动进度轮询（每 1s）
    const progressTimer = setInterval(async () => {
        try {
            const url = `${contextPath}/api/task-progress/${taskType}`;
            const progress = await fetchJsonForCurrentTaskModal(taskType, capturedGeneration, url);
            if (!progress) return;
            if (progress.executionId && currentTaskModal && !currentTaskModal.executionId) {
                currentTaskModal.executionId = progress.executionId;
                if (currentTaskModal.expandedExecutionId == null) {
                    currentTaskModal.expandedExecutionId = progress.executionId;
                    fetchRunList(taskType, capturedGeneration);
                }
            }
            updateTaskModalFromProgress(progress, capturedGeneration);
        } catch (e) { /* 静默 */ }
    }, 1000);

    // 启动日志轮询（每 3s）—— 仅当有展开的活动执行时拉取批次日志
    const logTimer = setInterval(async () => {
        try {
            if (!isCurrentTaskModal(taskType, capturedGeneration)) return;
            const expandedExecId = currentTaskModal?.expandedExecutionId;
            if (!expandedExecId) return;
            const cachedStatus = currentTaskModal?.runStatusByExecutionId?.[expandedExecId];
            if (!cachedStatus || (cachedStatus !== "RUNNING" && cachedStatus !== "CANCELLING")) return;
            await fetchAndCacheBatchLogs(taskType, expandedExecId, capturedGeneration);
        } catch (e) { /* 静默 */ }
    }, 3000);

    // 启动执行列表轮询（每 5s）
    const runListTimer = setInterval(async () => {
        try {
            if (!isCurrentTaskModal(taskType, capturedGeneration)) return;
            await fetchRunList(taskType, capturedGeneration);
        } catch (e) { /* 静默 */ }
    }, 5000);

    currentTaskModal.progressTimer = progressTimer;
    currentTaskModal.logTimer = logTimer;
    currentTaskModal.runListTimer = runListTimer;

        // 立即加载执行列表和日志
        fetchRunList(taskType, capturedGeneration);
    } catch (e) {
        console.error("Exception in openTaskModal:", e);
    }
}

function closeTaskModal() {
    if (!currentTaskModal) return;
    const taskType = currentTaskModal.taskType;
    const shouldWatch = shouldStartTaskWatcherOnClose(currentTaskModal);
    const awaitingLaunch = currentTaskModal.mode === "PROGRESS"
        && currentTaskModal.launchRequested
        && currentTaskModal.lastProgressStatus == null
        && currentTaskModal.executionId == null;

    stopTaskModalPolling();
    stopBatchSendStatusPoll();
    $("#taskProgressModal").hidden = true;
    document.body.classList.remove("modal-open");
    currentTaskModal = null;
    // 仅当已知活动状态时启动后台监视器；终态不启动，避免重复弹提示
    if (shouldWatch) {
        startTaskWatcher(taskType, { awaitingLaunch });
    }
}

function stopTaskModalPolling() {
    if (currentTaskModal) {
        if (currentTaskModal.progressTimer) clearInterval(currentTaskModal.progressTimer);
        if (currentTaskModal.logTimer) clearInterval(currentTaskModal.logTimer);
        if (currentTaskModal.runListTimer) clearInterval(currentTaskModal.runListTimer);
        currentTaskModal.progressTimer = null;
        currentTaskModal.logTimer = null;
        currentTaskModal.runListTimer = null;
    }
}

async function handleCancelTask() {
    if (!currentTaskModal) return;
    if (!confirm("确定要取消正在执行的任务吗？")) return;
    const taskType = currentTaskModal.taskType;
    const cancelBtn = $("#taskModalCancelBtn");
    cancelBtn.disabled = true;
    cancelBtn.textContent = "取消中...";
    try {
        await api(`/api/task-progress/${taskType}/cancel`, { method: "POST" });
        showStatus("已发送取消请求", "ok");
    } catch (e) {
        showStatus("取消失败: " + e.message, "error");
        cancelBtn.disabled = false;
        cancelBtn.textContent = "取消任务";
    }
}

// refreshRunListUntilExecutionTerminal is defined in task-modal-runtime.js

function renderRunList(runs, taskType, generation) {
    if (!isCurrentTaskModal(taskType, generation)) return;
    const runBody = $("#taskModalRunBody");
    if (!runBody) return;
    if (currentTaskModal) {
        runs.forEach(r => {
            currentTaskModal.runStatusByExecutionId[r.executionId] = r.status;
        });
    }
    const html = runs.length > 0
        ? runs.map(r => renderRunRow(r, taskType)).join("")
        : `<tr><td colspan="8" class="muted" style="text-align:center;padding:12px;">暂无执行记录</td></tr>`;
    // 内容未变化时跳过整表重写，避免每 5s 轮询导致的明显闪烁（及展开行被反复销毁重建）。
    if (runBody.__lastHtml === html) return;
    runBody.__lastHtml = html;
    runBody.innerHTML = html;
}

function updateExpandedFromCache(taskType, generation) {
    if (!isCurrentTaskModal(taskType, generation)) return;
    const expandedExecId = currentTaskModal?.expandedExecutionId;
    if (expandedExecId == null) return;
    const detailRow = document.getElementById(`detail-row-${expandedExecId}`);
    if (!detailRow) {
        const runRow = document.querySelector(`.run-row[data-execution-id="${expandedExecId}"]`);
        if (runRow) {
            const arrowCell = runRow.querySelector("td");
            if (arrowCell) arrowCell.textContent = "▼";
            runRow.insertAdjacentHTML("afterend", renderBatchDetailRow(expandedExecId));
        }
    }
    const cachedLogs = currentTaskModal?.batchLogsByExecutionId?.[expandedExecId];
    if (cachedLogs) {
        updateTaskModalLogs(expandedExecId, cachedLogs);
    }
}

function updateTaskModalFromProgress(progress, generation) {
    const statusEl = $("#taskModalStatus");
    const percentEl = $("#taskModalPercent");
    const fillEl = $("#taskModalFill");
    const messageEl = $("#taskModalMessage");
    const cancelBtn = $("#taskModalCancelBtn");
    const errorsDiv = $("#taskModalErrors");
    const errorContent = $("#taskModalErrorContent");

    statusEl.textContent = progress.status;
    statusEl.className = `task-modal-status ${progress.status.toLowerCase()}`;
    percentEl.textContent = progress.percentage + "%";
    fillEl.style.width = progress.percentage + "%";
    messageEl.textContent = progress.message || "";

    if (progress.errors && progress.errors.length > 0) {
        errorsDiv.hidden = false;
        errorContent.textContent = progress.errors.join("\n");
    }

    if (progress.details && progress.details.bySource) {
        const bySourceDiv = $("#taskModalBySource");
        const contentDiv = $("#taskModalBySourceContent");
        bySourceDiv.hidden = false;
        renderBySourceTable(progress.details.bySource, contentDiv);
        if (progress.details.summaryText) {
            contentDiv.innerHTML += `<div style="margin-top:6px;font-size:11px;color:var(--text-muted);">${escapeHtml(progress.details.summaryText)}</div>`;
        }
    }

    if (progress.details && progress.details.filterReasons != null) {
        messageEl.innerHTML = escapeHtml(progress.message || "") + renderFilterReasonsTable(progress.details.filterReasons);
    }
    if (progress.details && progress.details.demotionReasons != null) {
        messageEl.innerHTML = escapeHtml(progress.message || "") + renderFilterReasonsTable(progress.details.demotionReasons);
    }
    if (progress.details && progress.details.failureReasons != null) {
        messageEl.innerHTML = escapeHtml(progress.message || "") + renderFilterReasonsTable(progress.details.failureReasons);
    }

    if (cancelBtn && !cancelBtn.hidden && currentTaskModal && currentTaskModal.taskType === "EXPERT_ENRICHMENT"
        && progress.status === "RUNNING") {
        cancelBtn.textContent = "暂停";
    }

    // Batch send (MANUAL_INITIAL_OUTREACH): render per-account stats from progress.details (I-8).
    // Button/badge states are driven by the slower /batch-send/status poll (refreshBatchSendControls).
    if (currentTaskModal && currentTaskModal.taskType === "MANUAL_INITIAL_OUTREACH"
        && progress.details && progress.details.accounts != null) {
        const d = progress.details;
        renderBatchSendAccountTable({
            status: "RUNNING",
            mode: d.executionMode || "MANUAL",
            roundNumber: d.roundNumber ?? 0,
            dailyCap: d.dailyCap ?? 0,
            dailySentTotal: d.dailySentTotal ?? 0,
            sentTotal: d.sentTotal ?? 0,
            failedTotal: d.failedTotal ?? 0,
            accounts: d.accounts || []
        });
    }

    if (currentTaskModal) {
        const justObservedTerminal = observeTaskModalProgress(progress, generation);
        if (justObservedTerminal) {
            // 终态：直接隐藏取消按钮（终态状态已由上方 taskModalStatus 徽标表达），
            // 不再保留一个不可点击、改名为"已完成"的无意义按钮。
            cancelBtn.disabled = true;
            cancelBtn.hidden = true;
            if (currentTaskModal.btnId) {
                restoreTaskButton(currentTaskModal.btnId);
            }
            stopTaskWatcher(currentTaskModal.taskType, false);

            // Stop progress and log timers immediately
            if (currentTaskModal.progressTimer) {
                clearInterval(currentTaskModal.progressTimer);
                currentTaskModal.progressTimer = null;
            }
            if (currentTaskModal.logTimer) {
                clearInterval(currentTaskModal.logTimer);
                currentTaskModal.logTimer = null;
            }

            finalizeCurrentTaskModalTerminal(currentTaskModal.taskType, generation);
            // Batch send: refresh controls + banner on terminal so the operator sees the
            // resulting flow state (e.g. PAUSED + NO_AVAILABLE_ACCOUNT) immediately.
            if (currentTaskModal && currentTaskModal.taskType === "MANUAL_INITIAL_OUTREACH") {
                refreshBatchSendControls().catch(() => {});
            }
        }
    }

    // 同步旧进度条
    const bar = $("#taskProgressBar");
    const oldPercentEl = $("#taskProgressPercent");
    const oldFillEl = $("#taskProgressFill");
    const oldDetailEl = $("#taskProgressDetail");
    if (bar && !bar.hidden) {
        oldPercentEl.textContent = progress.percentage + "%";
        oldFillEl.style.width = progress.percentage + "%";
        oldDetailEl.textContent = progress.message || "";
        if (progress.status === "COMPLETED") {
            bar.className = "task-progress-bar completed";
        } else if (progress.status === "PARTIAL_SUCCESS") {
            bar.className = "task-progress-bar warning";
        } else {
            bar.className = "task-progress-bar failed";
        }
    }
}

function updateTaskModalLogs(executionId, logs) {
    // 刷新第一层执行行的内嵌批次表
    const batchBody = document.getElementById(`batch-body-${executionId}`);
    if (!batchBody) return;
    const html = renderBatchTable(logs);
    // 内容未变化时跳过重写，避免每 3s 日志轮询导致批次表闪烁。
    if (batchBody.__lastHtml === html) return;
    batchBody.__lastHtml = html;
    batchBody.innerHTML = html;
}

function renderBatchTable(logs) {
    if (!logs || logs.length === 0) {
        return `<tr><td colspan="7" class="muted" style="text-align:center;padding:12px;">暂无批次日志</td></tr>`;
    }
    const latestByBatch = new Map();
    logs.forEach(log => {
        const batchNumber = Number(log.batchNumber);
        if (!Number.isFinite(batchNumber) || batchNumber <= 0) return;
        latestByBatch.set(batchNumber, log);
    });
    const rows = Array.from(latestByBatch.entries())
        .sort(([a], [b]) => a - b)
        .map(([, log]) => log);
    if (rows.length === 0) {
        return `<tr><td colspan="7" class="muted" style="text-align:center;padding:12px;">暂无批次日志</td></tr>`;
    }
    return rows.map(log => {
        const time = formatDateTime(log.createdAt);
        const pct = log.totalCount > 0 ? Math.round((log.processedCount * 100) / log.totalCount) + "%" : "";
        let rejectReasons = "-";
        if (log.batchRejectReasonsJson) {
            try {
                const parsed = JSON.parse(log.batchRejectReasonsJson);
                rejectReasons = Object.entries(parsed)
                    .sort((a, b) => b[1] - a[1])
                    .slice(0, 3)
                    .map(([reason, count]) => `${reason}:${count}`)
                    .join(", ") || "-";
            } catch (e) {
                rejectReasons = "-";
            }
        }
        return `
            <tr>
                <td>${log.batchNumber}</td>
                <td>${log.batchProcessed}</td>
                <td>${log.batchPassed}</td>
                <td>${log.batchRejected}</td>
                <td>${log.processedCount}/${log.totalCount} ${pct}</td>
                <td style="max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(rejectReasons)}</td>
                <td>${time}</td>
            </tr>
        `;
    }).join("");
}

function renderRunRow(run, taskType) {
    const statusBadge = badge(
        run.status === "RUNNING" ? "运行中"
            : run.status === "CANCELLING" ? "取消中"
            : run.status === "SUCCESS" ? "成功"
            : run.status === "PARTIAL_SUCCESS" ? "部分成功"
            : run.status === "CANCELLED" ? "已取消"
            : run.status === "FAILED" ? "失败" : run.status,
        run.status === "RUNNING" || run.status === "CANCELLING" ? ""
            : run.status === "SUCCESS" ? "ok" : run.status === "FAILED" ? "error" : "warn"
    );
    const triggerLabel = run.triggerType === "SCHEDULED" ? "定时" : run.triggerType === "MANUAL" ? "手动" : run.triggerType;
    const duration = run.durationSeconds != null ? run.durationSeconds + "秒" : "-";
    const expanded = currentTaskModal?.expandedExecutionId === run.executionId;
    const arrow = expanded ? "▼" : "▶";
    return `
        <tr class="run-row" data-execution-id="${run.executionId}" data-status="${escapeHtml(run.status)}" onclick="toggleRunDetail('${escapeHtml(taskType)}', ${run.executionId})" style="cursor:pointer;">
            <td style="width:24px;text-align:center;">${arrow}</td>
            <td>${escapeHtml(run.startedAt)}</td>
            <td>${escapeHtml(triggerLabel)}</td>
            <td>${statusBadge}</td>
            <td>${run.totalProcessed}</td>
            <td>${run.totalPassed}</td>
            <td>${run.totalRejected}</td>
            <td>${escapeHtml(duration)}</td>
        </tr>
    `;
}

function renderBatchDetailRow(executionId) {
    return `
        <tr class="run-detail-row" id="detail-row-${executionId}">
            <td colspan="8" style="padding:0;">
                <div style="max-height:200px;overflow-y:auto;margin:4px 0;">
                    <table class="data-table compact" style="width:100%;border-collapse:collapse;font-size:11px;">
                        <thead>
                            <tr style="background-color:var(--panel-bg);">
                                <th style="padding:6px;">批次</th>
                                <th style="padding:6px;">本批处理</th>
                                <th style="padding:6px;">通过</th>
                                <th style="padding:6px;">拒绝</th>
                                <th style="padding:6px;">累计进度</th>
                                <th style="padding:6px;">失败原因</th>
                                <th style="padding:6px;">时间</th>
                            </tr>
                        </thead>
                        <tbody id="batch-body-${executionId}">
                            <tr><td colspan="7" class="muted" style="text-align:center;padding:12px;">加载中...</td></tr>
                        </tbody>
                    </table>
                </div>
            </td>
        </tr>
    `;
}

async function toggleRunDetail(taskType, executionId) {
    if (!currentTaskModal) return;
    const prevExpanded = currentTaskModal.expandedExecutionId;
    const generation = currentTaskModal.generation;

    if (prevExpanded != null) {
        const prevRow = document.getElementById(`detail-row-${prevExpanded}`);
        if (prevRow) prevRow.remove();
        const prevRunRow = document.querySelector(`.run-row[data-execution-id="${prevExpanded}"]`);
        if (prevRunRow) {
            const arrowCell = prevRunRow.querySelector("td");
            if (arrowCell) arrowCell.textContent = "▶";
        }
    }

    if (prevExpanded === executionId) {
        currentTaskModal.expandedExecutionId = null;
        return;
    }

    currentTaskModal.expandedExecutionId = executionId;
    const cachedStatus = currentTaskModal.runStatusByExecutionId?.[executionId];
    const runRow = document.querySelector(`.run-row[data-execution-id="${executionId}"]`);
    if (runRow) {
        const arrowCell = runRow.querySelector("td");
        if (arrowCell) arrowCell.textContent = "▼";
        runRow.insertAdjacentHTML("afterend", renderBatchDetailRow(executionId));
    }

    // Use cache for historical (terminal) executions
    const cachedLogs = currentTaskModal.batchLogsByExecutionId?.[executionId];
    if (cachedLogs && cachedStatus && isExecutionTerminal(cachedStatus)) {
        updateTaskModalLogs(executionId, cachedLogs);
        return;
    }

    try {
        await fetchAndCacheBatchLogs(taskType, executionId, generation);
    } catch (e) { /* 静默 */ }
}

async function fetchRunList(taskType, generation) {
    try {
        const url = `${contextPath}/api/task-progress/${taskType}/executions?limit=10`;
        const runs = await fetchJsonForCurrentTaskModal(taskType, generation, url);
        if (!runs) return;
        const selectedRun = selectExecutionForCurrentModal(runs, currentTaskModal);
        if (selectedRun && isCurrentTaskModal(taskType, generation)) {
            await adoptTaskModalExecution(taskType, generation, selectedRun.executionId, "RUN_LIST");
        }
        renderRunList(runs, taskType, generation);
        updateExpandedFromCache(taskType, generation);
    } catch (e) { /* 静默 */ }
}

function renderBySourceTable(bySource, container) {
    if (!bySource || Object.keys(bySource).length === 0) {
        container.innerHTML = "";
        return;
    }
    const rows = Object.entries(bySource).map(([name, stats]) => {
        const failures = stats.failureReasons ? Object.entries(stats.failureReasons)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([reason, count]) => `${reason}:${count}`)
            .join(", ") : "-";
        return `
            <tr>
                <td style="padding:3px 8px;">${escapeHtml(name)}</td>
                <td style="padding:3px 8px;">${escapeHtml(stats.extractionMethod || "-")}</td>
                <td style="padding:3px 8px;">${stats.papersSearched || 0}</td>
                <td style="padding:3px 8px;">${stats.authorsExtracted || 0}</td>
                <td style="padding:3px 8px;">${stats.emailsValid || 0}</td>
                <td style="padding:3px 8px;">${stats.indexed || 0}</td>
                <td style="padding:3px 8px;">${stats.promoted || 0}</td>
                <td style="padding:3px 8px;max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(failures)}</td>
            </tr>
        `;
    }).join("");
    container.innerHTML = `
        <table style="width:100%;border-collapse:collapse;font-size:11px;">
            <thead><tr style="background:var(--panel-bg);border-bottom:1px solid var(--panel-border);">
                <th style="padding:4px 8px;text-align:left;">平台</th>
                <th style="padding:4px 8px;text-align:left;">方式</th>
                <th style="padding:4px 8px;text-align:left;">论文</th>
                <th style="padding:4px 8px;text-align:left;">邮箱</th>
                <th style="padding:4px 8px;text-align:left;">有效</th>
                <th style="padding:4px 8px;text-align:left;">收录</th>
                <th style="padding:4px 8px;text-align:left;">晋升</th>
                <th style="padding:4px 8px;text-align:left;">失败原因</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
    `;
}

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

function focusMailboxProcessingPanel() {
    const panel = $("#unmatchedDetailPanel");
    const scrollContainer = document.querySelector(".main");
    if (!panel) return;

    requestAnimationFrame(() => {
        if (!scrollContainer) {
            panel.scrollIntoView({ behavior: "smooth", block: "start" });
            return;
        }
        const top = Math.max(
            0,
            scrollContainer.scrollTop
                + panel.getBoundingClientRect().top
                - scrollContainer.getBoundingClientRect().top
                - 12
        );
        scrollContainer.scrollTo({ top, behavior: "smooth" });
    });
}

async function api(path, options = {}) {
    const response = await fetch(`${contextPath}${path}`, {
        headers: { "Content-Type": "application/json", ...(options.headers || {}) },
        ...options
    });
    await handleAuthResponse(response);
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

function encodeTranslateSrc(text) {
    try {
        return btoa(unescape(encodeURIComponent(String(text ?? ""))));
    } catch {
        return "";
    }
}

function decodeTranslateSrc(encoded) {
    try {
        return decodeURIComponent(escape(atob(encoded)));
    } catch {
        return "";
    }
}

function translatableBody(text, opts = {}) {
    const raw = String(text ?? "");
    const display = !raw.trim() && opts.emptyLabel ? opts.emptyLabel : raw;
    const srcRaw = opts.translateSrc != null ? String(opts.translateSrc) : raw;
    const encoded = encodeTranslateSrc(srcRaw);
    return `
        <div class="translatable-body-block">
            <div class="pre translatable-body" data-translate-src="${encoded}">${escapeHtml(display)}</div>
            <button class="btn-translate" type="button">🌐 翻译为中文</button>
            <div class="translation-text pre" hidden></div>
        </div>
    `;
}

let translateClickHandlerBound = false;

function ensureTranslateClickHandler() {
    if (translateClickHandlerBound) return;
    translateClickHandlerBound = true;
    document.addEventListener("click", (event) => {
        const btn = event.target.closest(".btn-translate");
        if (!btn) return;
        event.preventDefault();
        onTranslateClick(btn);
    });
}

async function onTranslateClick(btn) {
    const block = btn.closest(".translatable-body-block");
    if (!block) return;
    const translationEl = block.querySelector(".translation-text");
    const srcEl = block.querySelector(".translatable-body");
    const srcEncoded = srcEl?.dataset.translateSrc;
    if (!srcEncoded || !translationEl) return;

    if (btn.dataset.state === "expanded") {
        translationEl.hidden = true;
        btn.textContent = "🌐 翻译为中文";
        btn.dataset.state = "collapsed";
        return;
    }
    if (btn.dataset.state === "collapsed" && translationEl.innerHTML) {
        translationEl.hidden = false;
        btn.textContent = "收起译文";
        btn.dataset.state = "expanded";
        return;
    }

    const text = decodeTranslateSrc(srcEncoded);
    btn.disabled = true;
    btn.textContent = "翻译中…";
    try {
        const result = await api("/api/translate", {
            method: "POST",
            body: JSON.stringify({ text })
        });
        if (result.ok && result.translatedText) {
            translationEl.innerHTML = escapeHtml(result.translatedText);
            translationEl.hidden = false;
            btn.textContent = "收起译文";
            btn.dataset.state = "expanded";
        } else {
            btn.textContent = "翻译失败，重试";
            btn.dataset.state = "retry";
        }
    } catch {
        btn.textContent = "翻译失败，重试";
        btn.dataset.state = "retry";
    } finally {
        btn.disabled = false;
    }
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

function optionsFromArray(arr, includeBlank = false, blankLabel = "全部", selectedValue = "") {
    const opts = [];
    if (includeBlank) opts.push(`<option value="">${escapeHtml(blankLabel)}</option>`);
    for (const [value, label] of arr) {
        const sel = selectedValue === value ? " selected" : "";
        opts.push(`<option value="${escapeHtml(value)}"${sel}>${escapeHtml(label)}</option>`);
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
    if (view === "mail-templates") {
        state.mailSendOptions = [];
    }
    $$(".nav-tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.view === view));
    $$(".view").forEach((section) => section.classList.toggle("active", section.id === `view-${view}`));
    $("#viewTitle").textContent = viewMeta[view][0];
    $("#viewSubtitle").textContent = viewMeta[view][1];
    refreshCurrentView();
    if (view === "contacts") {
        resumeProgressPollingIfNeeded();
    } else {
        ["EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY"].forEach(t => stopTaskWatcher(t, true));
    }
}

async function refreshCurrentView() {
    try {
        if (state.view === "accounts") await loadAccounts();
        if (state.view === "mail-templates") await loadMailTemplatesView();
        if (state.view === "suppressions") await loadSuppressions();
        if (state.view === "contacts") await loadContacts();
        if (state.view === "mailbox") await loadMailbox();
        if (state.view === "inbound-summary") await loadInboundSummary();
        if (state.view === "ai-training") await loadAiTraining();
        if (state.view === "tasks") await loadTasks();
        if (state.view === "monitoring") await loadMonitoring();
    } catch (error) {
        showStatus(error.message, "error");
    }
}

async function loadAccounts() {
    state.accounts = await api("/api/mail/sender-accounts");
    $("#accountsTable").innerHTML = state.accounts.map((account) => {
        const autoPaused = account.autoSendPaused === true;
        const statusCell = badge(account.enabled ? "启用" : "禁用", account.enabled ? "ok" : "error")
            + (autoPaused
                ? ` <span class="badge warn" title="${escapeHtml(account.autoSendPausedReason || "自动暂停")}">自动暂停</span>`
                : "");
        const actions = [
            `<button class="button" data-action="view-account" data-code="${escapeHtml(account.accountCode)}">查看</button>`,
            `<button class="button" data-action="edit-account" data-code="${escapeHtml(account.accountCode)}">编辑</button>`,
            `<button class="button" data-action="test-account" data-code="${escapeHtml(account.accountCode)}">测试</button>`,
            `<button class="button" data-action="toggle-account" data-code="${escapeHtml(account.accountCode)}" data-enabled="${account.enabled}">
                ${account.enabled ? "禁用" : "启用"}
            </button>`,
            `<button class="button" data-action="reset-account" data-code="${escapeHtml(account.accountCode)}">重置</button>`,
            `<button class="button" data-action="delete-account" data-code="${escapeHtml(account.accountCode)}">删除</button>`
        ];
        if (autoPaused) {
            actions.push(`<button class="button" data-action="resume-auto-send" data-code="${escapeHtml(account.accountCode)}">恢复发送</button>`);
        }
        return `
        <tr>
            <td><strong>${escapeHtml(account.accountCode)}</strong></td>
            <td>${escapeHtml(account.senderEmail)}</td>
            <td>${account.strategyWeight}</td>
            <td>${account.todaySentCount}/${account.effectiveDailyLimit}${
                account.effectiveDailyLimit < account.dailySendLimit
                    ? ' <span class="badge info">预热中</span>'
                    : ""
            }</td>
            <td>${statusCell}</td>
            <td class="actions">${actions.join("")}</td>
        </tr>
    `;
    }).join("");
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
    state.currentEditAccount = null;
    $("#accountStatusBadge").hidden = true;
    $("#accountEditorSubtitle").hidden = true;
    $("#warmupStatusBadge").hidden = true;
    $("#effectiveLimitHint").textContent = "";
    $("#deleteAccountBtn").hidden = true;
    $("#warmupFields").hidden = true;
    $("#warmupCustomStepsRow").hidden = true;
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

function toDatetimeLocalValue(value) {
    if (!value) {
        return "";
    }
    return value.length >= 16 ? value.slice(0, 16) : value;
}

function updateWarmupFieldsVisibility() {
    const enabled = $("#warmupEnabledCheckbox").checked;
    $("#warmupFields").hidden = !enabled;
}

function updateWarmupStepsMode() {
    const mode = $("#warmupStepsMode").value;
    $("#warmupCustomStepsRow").hidden = mode !== "custom";
}

function updateAccountStatusBadge(account) {
    const badge = $("#accountStatusBadge");
    if (!account) {
        badge.hidden = true;
        return;
    }
    badge.hidden = false;
    if (account.autoSendPaused) {
        badge.textContent = "自动暂停";
        badge.className = "badge warn";
    } else if (account.enabled) {
        badge.textContent = "已启用";
        badge.className = "badge ok";
    } else {
        badge.textContent = "已禁用";
        badge.className = "badge error";
    }
}

function updateEffectiveLimitHint(account) {
    const hint = $("#effectiveLimitHint");
    if (!account || account.effectiveDailyLimit == null) {
        hint.textContent = "";
        return;
    }
    if (account.effectiveDailyLimit < account.dailySendLimit) {
        hint.textContent = `(有效: ${account.effectiveDailyLimit})`;
    } else {
        hint.textContent = "";
    }
}

function updateWarmupStatusBadge(account) {
    const badge = $("#warmupStatusBadge");
    if (!account || account.warmupEnabled !== true || !account.warmupStartedAt) {
        badge.hidden = true;
        return;
    }
    const startedAt = new Date(account.warmupStartedAt);
    const now = new Date();
    const dayNum = Math.floor((now - startedAt) / (1000 * 60 * 60 * 24)) + 1;
    badge.textContent = `预热中 · 第${dayNum}天`;
    badge.hidden = false;
}

function fillAccountForm(account, mode = account ? "edit" : "new") {
    const form = $("#accountForm");
    showAccountEditor();
    state.selectedAccount = account?.accountCode || null;
    state.accountEditorMode = mode;
    state.currentEditAccount = account || null;
    $("#accountEditorTitle").textContent = account
        ? `${mode === "view" ? "查看账号" : "编辑账号"}：${account.accountCode}`
        : "新增账号";
    const subtitle = $("#accountEditorSubtitle");
    if (account?.senderEmail) {
        subtitle.textContent = account.senderEmail;
        subtitle.hidden = false;
    } else {
        subtitle.textContent = "";
        subtitle.hidden = true;
    }
    document.querySelectorAll("#accountForm .password-field input").forEach((input) => {
        input.type = "password";
    });
    document.querySelectorAll("#accountForm .password-toggle").forEach((btn) => {
        btn.textContent = "显示";
    });
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
    const isEdit = mode === "edit";
    form.smtpPassword.required = !isEdit;
    form.imapPassword.required = !isEdit;
    form.smtpPassword.placeholder = isEdit ? "留空保持不变" : "授权码或账号密码";
    form.imapPassword.placeholder = isEdit ? "留空保持不变" : "授权码或账号密码";
    form.enabled.checked = account?.enabled ?? false;
    form.warmupEnabled.checked = account?.warmupEnabled === true;
    form.warmupStartedAt.value = toDatetimeLocalValue(account?.warmupStartedAt);
    const hasCustomSteps = account?.warmupStepsJson && account.warmupStepsJson.trim();
    form.warmupStepsMode.value = hasCustomSteps ? "custom" : "default";
    form.warmupStepsJson.value = account?.warmupStepsJson ?? "";
    updateWarmupFieldsVisibility();
    updateWarmupStepsMode();
    updateAccountStatusBadge(account);
    updateEffectiveLimitHint(account);
    updateWarmupStatusBadge(account);
    const deleteBtn = $("#deleteAccountBtn");
    deleteBtn.hidden = !account || mode === "view" || account.accountCode === "SIMULATOR_NOOP";
    deleteBtn.disabled = mode === "view";
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
        payload.smtpPassword = values.smtpPassword?.trim() || null;
        payload.imapPassword = values.imapPassword?.trim() || null;
        payload.warmupEnabled = form.warmupEnabled.checked ? true : false;
        payload.warmupStartedAt = values.warmupStartedAt?.trim() || null;
        payload.warmupStepsJson = form.warmupStepsMode.value === "custom"
            ? (values.warmupStepsJson?.trim() || null)
            : null;
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
        try {
            await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}/${enabled ? "disable" : "enable"}`, { method: "POST" });
            await loadAccounts();
        } catch (error) {
            showStatus(error.message, "error");
        }
    }
    if (action === "delete-account") {
        if (!confirm(`确认删除账号 ${code}？此操作不可恢复。`)) {
            return;
        }
        try {
            await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}`, { method: "DELETE" });
            showStatus(`账号 ${code} 已删除`, "ok");
            await loadAccounts();
        } catch (error) {
            showStatus(error.message, "error");
        }
    }
    if (action === "reset-account") {
        await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}/reset-today-sent-count`, { method: "POST" });
        await loadAccounts();
    }
    if (action === "resume-auto-send") {
        await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}/resume-auto-send`, { method: "POST" });
        showStatus(`账号 ${code} 已恢复自动发送`, "ok");
        await loadAccounts();
        refreshBatchSendBanner().catch(() => {});
    }
}

function renderQaCoverageKeyOptions(selectedKeys) {
    const container = $("#qaCoverageKeyOptions");
    const warning = $("#qaCoverageKeyWarning");
    if (!container) return;
    const selected = new Set(selectedKeys || []);
    const groups = new Map();
    state.qaCoverageKeys.forEach((entry) => {
        const g = entry.group || "其他";
        if (!groups.has(g)) groups.set(g, []);
        groups.get(g).push(entry);
    });
    container.innerHTML = "";
    groups.forEach((entries, group) => {
        const title = document.createElement("div");
        title.className = "field-label";
        title.style.cssText = "grid-column:1/-1;margin-top:6px;";
        title.textContent = group;
        container.appendChild(title);
        entries.forEach((entry) => {
            const row = document.createElement("label");
            row.className = "checkbox-row";
            row.title = escapeHtml(entry.description || "");
            row.innerHTML = `<input type="checkbox" data-qa-coverage-key="${escapeHtml(entry.key)}" ${selected.has(entry.key) ? "checked" : ""}>
                <span>${escapeHtml(entry.label)}</span>`;
            container.appendChild(row);
        });
    });
    if (warning) {
        warning.style.display = selected.size > 0 ? "none" : "";
    }
}

async function loadQa() {
    const [categories, rules] = await Promise.all([
        api("/api/qa/categories"),
        api("/api/qa/rules")
    ]);
    state.categories = categories;
    state.qaRules = rules;

    const formSelect = $("#qaRuleForm").categoryId;
    const formSelectValue = formSelect.value;
    formSelect.innerHTML = `<option value="">选择事实分类</option>` + categories.map((category) => `
        <option value="${category.id}">${escapeHtml(category.categoryName)}</option>
    `).join("");
    formSelect.value = formSelectValue;

    renderQaRulesTable();
    renderQaAuditPanel();
}

async function loadQaAuditReport() {
    const fromInput = $("#qaAuditFrom");
    const toInput = $("#qaAuditTo");
    const from = fromInput?.value;
    const to = toInput?.value;
    if (!from || !to) {
        showStatus("请选择审计时间范围", "error");
        return;
    }
    state.qaAudit.from = from;
    state.qaAudit.to = to;
    const report = await api(`/api/qa/audit/rule-usage?from=${encodeURIComponent(from)}T00:00:00&to=${encodeURIComponent(to)}T23:59:59`);
    state.qaAudit.report = report;
    renderQaAuditPanel();
}

function renderQaAuditPanel() {
    const container = $("#qaAuditPanel");
    if (!container) return;
    const report = state.qaAudit.report;
    if (!report) {
        container.innerHTML = `<p class="text-muted" style="font-size:13px;">选择时间段后点击加载报表。</p>`;
        return;
    }
    const renderCounts = (rows, label) => {
        if (!rows?.length) return `<p class="text-muted" style="font-size:12px;">${label}：无数据</p>`;
        return `
            <h4 style="margin:8px 0 4px;font-size:13px;">${label}</h4>
            <ul class="compose-gap-list">
                ${rows.map((row) => `<li><span>规则 #${row.qaRuleId}</span><span class="badge ok">${row.count} 次</span></li>`).join("")}
            </ul>`;
    };
    const renderQualityMetrics = (qm) => {
        if (!qm) return `<p class="text-muted" style="font-size:12px;">AI 回复质量指标：无数据</p>`;
        const safeNum = (v) => (v != null ? v : 0);
        const total = safeNum(qm.totalGenerated);
        return `
            <h4 style="margin:8px 0 4px;font-size:13px;">AI 回复质量指标</h4>
            <div class="metadata-grid">
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>AI 初稿总数</span></div>
                    <div class="metadata-card-value">${total}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>完整率 (READY)</span></div>
                    <div class="metadata-card-value">${formatPercent(qm.readyRate)}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>部分覆盖率 (NEEDS_REVIEW)</span></div>
                    <div class="metadata-card-value">${formatPercent(qm.partialRate)}</div>
                </div>
                <div class="metadata-card">
                    <div class="metadata-card-header"><span>遗漏率 (BLOCKED)</span></div>
                    <div class="metadata-card-value">${formatPercent(qm.omissionRate)}</div>
                </div>
            </div>`;
    };
    container.innerHTML = `
        <div class="metadata-grid" style="margin-bottom:12px;">
            <div class="metadata-card"><div class="metadata-card-header"><span>组装回复次数</span></div><div class="metadata-card-value">${report.totalActions}</div></div>
            <div class="metadata-card"><div class="metadata-card-header"><span>人工改写次数</span></div><div class="metadata-card-value">${report.editedReplyCount}</div></div>
        </div>
        ${renderQualityMetrics(report.aiReplyQuality)}
        ${renderCounts(report.removedRuleCounts, "运营移除的建议规则（疑似误命中）")}
        ${renderCounts(report.addedRuleCounts, "运营新增的规则（疑似缺失/盲区）")}
        ${renderFreeTextTopics(report.freeTextTopicCounts || [])}
    `;
}

function renderFreeTextTopics(rows) {
    if (!rows?.length) {
        return `<p class="text-muted" style="font-size:12px;">高频自由文本主题：无数据</p>`;
    }
    return `
        <h4 style="margin:8px 0 4px;font-size:13px;">高频自由文本主题</h4>
        <ul class="compose-gap-list">
            ${rows.map((row) => `
                <li><span>${escapeHtml(row.topic)}</span><span class="badge ok">${row.count} 次</span></li>
            `).join("")}
        </ul>`;
}

function renderQaCoverageKeyLabels(coverageKeys) {
    const keys = coverageKeys || [];
    if (keys.length === 0) return badge("未配置 AI 覆盖能力", "warn");
    const labels = keys.map((key) => {
        const entry = state.qaCoverageKeys.find((ek) => ek.key === key);
        return entry ? entry.label : key;
    });
    if (labels.length <= 3) return labels.map(escapeHtml).join(" · ");
    const shown = labels.slice(0, 3).map(escapeHtml).join(" · ");
    return `${shown} <span class="muted">另 ${labels.length - 3} 项</span>`;
}

function qaReplyPolicyBadge(rule) {
    const policy = rule.replyPolicy || "REVIEW";
    if (policy === "AUTO") {
        return badge("AUTO", "ok");
    }
    if (policy === "NEVER") {
        return badge("NEVER", "error");
    }
    return badge("REVIEW", "warn");
}

function qaFactBodyPreview(text) {
    const value = String(text || "");
    if (value.length <= 120) {
        return escapeHtml(value);
    }
    return `${escapeHtml(value.slice(0, 120))}…`;
}

function renderQaRulesTable() {
    $("#qaRulesTable").innerHTML = state.qaRules.map((rule) => {
        const displayName = rule.displayName?.trim();
        const nameCell = displayName
            ? escapeHtml(displayName)
            : `<span class="muted">（未设置标题）</span>`;
        const factBody = rule.answerBody || rule.replyBody || "";
        return `
        <tr>
            <td>${rule.id}</td>
            <td>${nameCell}</td>
            <td>${escapeHtml(rule.categoryName || rule.categoryCode || rule.categoryId)}</td>
            <td class="muted-cell">${escapeHtml(rule.keywords)}</td>
            <td class="muted-cell">${qaFactBodyPreview(factBody)}</td>
            <td>${rule.priority}</td>
            <td>${qaReplyPolicyBadge(rule)}</td>
            <td>${badge(rule.enabled ? "启用" : "禁用", rule.enabled ? "ok" : "warn")}</td>
            <td class="actions">
                <button class="button" data-action="edit-rule" data-id="${rule.id}">编辑</button>
                <button class="button" data-action="toggle-rule" data-id="${rule.id}" data-enabled="${rule.enabled}">
                    ${rule.enabled ? "禁用" : "启用"}
                </button>
            </td>
        </tr>
    `;
    }).join("") || `<tr><td colspan="9" class="muted" style="text-align:center; padding:20px;">暂无 QA 事实</td></tr>`;
}

async function ensureVariableMeta() {
    if (state.variableMetaLoaded) {
        return state.variableMeta || [];
    }
    state.variableMeta = await api("/api/qa/template-variables-meta");
    state.variableMetaLoaded = true;
    return state.variableMeta;
}

function resolveVarTextarea(targetId) {
    return document.getElementById(targetId)
        || document.querySelector(`textarea[name="${targetId}"]`)
        || document.querySelector(`input[name="${targetId}"]`);
}

const EXPERT_VAR_KEY_SET = new Set([
    "expertName", "expertFamilyName", "researchFields", "institution", "keyword",
    "expertCountry", "employment", "hIndex", "worksCount", "lastPublicationYear",
    "degree", "recentWorkTitle", "patentTitle"
]);
const SENDER_VAR_KEY_SET = new Set([
    "senderEmail", "senderName", "senderTitle", "teamName", "countryName", "senderDisplayName"
]);

let previewDrawerDebounceTimer = null;
let previewDrawerCollapseTimer = null;

function isPreviewDrawerOpen() {
    const shell = $("#previewDrawer");
    return Boolean(shell && !shell.hidden);
}

function isComposeTemplatePreviewTarget() {
    return state.previewDrawer.targetId === "composeTemplate";
}

function schedulePreviewDrawerRefresh() {
    if (!isPreviewDrawerOpen()) return;
    window.clearTimeout(previewDrawerDebounceTimer);
    previewDrawerDebounceTimer = window.setTimeout(() => {
        refreshPreviewDrawer().catch((error) => showStatus(error.message, "error"));
    }, 600);
}

function updatePreviewContextPanels() {
    const fieldCtx = $("#previewFieldContext");
    const composeCtx = $("#previewComposeContext");
    const isCompose = isComposeTemplatePreviewTarget();
    if (fieldCtx) fieldCtx.hidden = isCompose;
    if (composeCtx) composeCtx.hidden = !isCompose;
}

function updatePreviewVariantSwitcher(poolSize) {
    const switcher = $("#previewVariantSwitcher");
    const label = $("#previewVariantLabel");
    if (!switcher || !label) return;
    const size = poolSize ?? state.previewDrawer.variantPoolSize ?? 1;
    state.previewDrawer.variantPoolSize = size;
    if (size <= 1) {
        switcher.hidden = true;
        state.previewDrawer.variantIndex = 0;
        label.textContent = "组合 1/1";
        return;
    }
    if (state.previewDrawer.variantIndex >= size) {
        state.previewDrawer.variantIndex = 0;
    }
    switcher.hidden = false;
    label.textContent = `组合 ${state.previewDrawer.variantIndex + 1}/${size}`;
}

function stepPreviewVariantIndex(delta) {
    const size = state.previewDrawer.variantPoolSize || 1;
    if (size <= 1) return;
    let next = state.previewDrawer.variantIndex + delta;
    if (next < 0) next = size - 1;
    if (next >= size) next = 0;
    state.previewDrawer.variantIndex = next;
    updatePreviewVariantSwitcher(size);
    refreshPreviewDrawer().catch((error) => showStatus(error.message, "error"));
}

function closeOpenVarInsertMenus(exceptWrap) {
    document.querySelectorAll(".var-insert-wrap").forEach((wrap) => {
        if (exceptWrap && wrap === exceptWrap) return;
        const menu = wrap.querySelector(".var-insert-menu");
        if (menu) menu.hidden = true;
    });
}

function parsePlaceholderToken(inner) {
    const pipeIndex = inner.indexOf("|");
    if (pipeIndex >= 0) {
        return {
            key: inner.slice(0, pipeIndex),
            fallback: inner.slice(pipeIndex + 1)
        };
    }
    return { key: inner, fallback: null };
}

function validatePlaceholderText(text) {
    if (!text) {
        return { valid: true, violations: [] };
    }
    const metaByKey = Object.fromEntries((state.variableMeta || []).map((meta) => [meta.key, meta]));
    const violations = [];
    const regex = /\$\{([^}]*)\}/g;
    let match;
    while ((match = regex.exec(text)) !== null) {
        const parsed = parsePlaceholderToken(match[1]);
        const meta = metaByKey[parsed.key];
        if (!meta) {
            violations.push(match[0]);
            continue;
        }
        if (meta.nullable && (!parsed.fallback || !parsed.fallback.trim().length)) {
            violations.push(match[0]);
        }
    }
    return { valid: violations.length === 0, violations };
}

function placeholderDefaultFallback(key) {
    return {
        expertName: "Professor",
        expertFamilyName: "Professor",
        researchFields: "your research field",
        institution: "your institution",
        keyword: "your area of expertise",
        expertCountry: "your country",
        employment: "your current role",
        hIndex: "your academic impact",
        worksCount: "your publications",
        lastPublicationYear: "recent years",
        degree: "your academic background",
        recentWorkTitle: "your recent research",
        patentTitle: "your innovation work"
    }[key] || "";
}

function renderVarChipButtons(targetId, metas) {
    return (metas || []).map((meta) => `
        <button type="button" class="var-chip"
            data-var-target="${escapeHtml(targetId)}"
            data-var-key="${escapeHtml(meta.key)}"
            data-var-nullable="${meta.nullable ? "true" : "false"}"
            data-var-fallback="${escapeHtml(placeholderDefaultFallback(meta.key))}"
            title="${escapeHtml(meta.key)}${meta.example ? ` — ${escapeHtml(meta.example)}` : ""}">
            ${escapeHtml(meta.label)}
        </button>`).join("");
}

function renderVarChipBarContent(targetId) {
    return renderVarChipButtons(targetId, state.variableMeta || []);
}

function renderVarInsertMenuContent(targetId) {
    const metas = state.variableMeta || [];
    const senderMetas = metas.filter((meta) => SENDER_VAR_KEY_SET.has(meta.key));
    const expertMetas = metas.filter((meta) => EXPERT_VAR_KEY_SET.has(meta.key));
    const otherMetas = metas.filter(
        (meta) => !SENDER_VAR_KEY_SET.has(meta.key) && !EXPERT_VAR_KEY_SET.has(meta.key)
    );
    const otherGroup = otherMetas.length
        ? `<p class="var-insert-group-label">其他</p>
        <div class="var-insert-group">${renderVarChipButtons(targetId, otherMetas)}</div>`
        : "";
    return `
        <p class="var-insert-group-label">发送方</p>
        <div class="var-insert-group">${renderVarChipButtons(targetId, senderMetas)}</div>
        <p class="var-insert-group-label">专家</p>
        <div class="var-insert-group">${renderVarChipButtons(targetId, expertMetas)}</div>
        ${otherGroup}`;
}

function renderVarInsertMenu(wrap, targetId) {
    const menu = wrap?.querySelector(".var-insert-menu");
    if (!menu) return;
    menu.innerHTML = renderVarInsertMenuContent(targetId);
    bindVarChipBar(menu);
}

function rememberVarSelection(textarea) {
    if (!textarea) return;
    try {
        textarea.dataset.varSelStart = String(textarea.selectionStart ?? "");
        textarea.dataset.varSelEnd = String(textarea.selectionEnd ?? "");
    } catch (_) {
        // ignore selection access errors on unsupported fields
    }
}

function resolveVarInsertRange(textarea) {
    if (document.activeElement === textarea) {
        return { start: textarea.selectionStart, end: textarea.selectionEnd };
    }
    const startRaw = textarea.dataset?.varSelStart;
    const endRaw = textarea.dataset?.varSelEnd;
    if (startRaw !== undefined && startRaw !== "" && endRaw !== undefined && endRaw !== "") {
        const start = Number(startRaw);
        const end = Number(endRaw);
        if (Number.isFinite(start) && Number.isFinite(end)) {
            const len = (textarea.value || "").length;
            return {
                start: Math.max(0, Math.min(start, len)),
                end: Math.max(0, Math.min(end, len))
            };
        }
    }
    const len = (textarea.value || "").length;
    return { start: len, end: len };
}

function insertVarAtCursor(textarea, insertText, cursorOffset) {
    const { start, end } = resolveVarInsertRange(textarea);
    const before = textarea.value.slice(0, start);
    const after = textarea.value.slice(end);
    textarea.value = before + insertText + after;
    const pos = start + insertText.length - cursorOffset;
    textarea.selectionStart = pos;
    textarea.selectionEnd = pos;
    rememberVarSelection(textarea);
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
    textarea.focus();
}

function updateVarValidationForTarget(targetId, textarea) {
    const hint = document.getElementById(`varHint-${targetId}`);
    const form = textarea?.closest("form");
    const submitBtn = form?.querySelector('button[type="submit"]');
    const { valid, violations } = validatePlaceholderText(textarea?.value || "");
    if (hint) {
        if (!valid) {
            hint.hidden = false;
            hint.className = "var-validation-hint invalid";
            hint.textContent = `非法占位符：${violations.join(", ")}`;
        } else {
            hint.hidden = true;
            hint.className = "var-validation-hint";
            hint.textContent = "";
        }
    }
    if (submitBtn) {
        submitBtn.disabled = !valid;
    }
    return valid;
}

function bindVarChipBar(container) {
    if (!container) return;
    container.querySelectorAll(".var-chip").forEach((chip) => {
        if (chip.dataset.bound === "1") return;
        chip.dataset.bound = "1";
        chip.addEventListener("click", () => {
            const targetId = chip.dataset.varTarget;
            const textarea = resolveVarTextarea(targetId);
            if (!textarea) return;
            const key = chip.dataset.varKey;
            const nullable = chip.dataset.varNullable === "true";
            const fallback = chip.dataset.varFallback || "";
            const insertText = nullable ? `\${${key}|${fallback}}` : `\${${key}}`;
            const cursorOffset = nullable && !fallback ? 1 : 0;
            insertVarAtCursor(textarea, insertText, cursorOffset);
            updateVarValidationForTarget(targetId, textarea);
        });
    });
}

function initVarEditorForTextarea(textarea) {
    if (!textarea || textarea.dataset.varEditorBound === "1") return;
    textarea.dataset.varEditorBound = "1";
    const targetId = textarea.id || textarea.name;
    const remember = () => rememberVarSelection(textarea);
    textarea.addEventListener("focus", remember);
    textarea.addEventListener("keyup", remember);
    textarea.addEventListener("mouseup", remember);
    textarea.addEventListener("select", remember);
    textarea.addEventListener("blur", () => updateVarValidationForTarget(targetId, textarea));
    textarea.addEventListener("input", () => {
        rememberVarSelection(textarea);
        updateVarValidationForTarget(targetId, textarea);
        if (isPreviewDrawerOpen() && state.previewDrawer.targetId === targetId) {
            schedulePreviewDrawerRefresh();
        }
    });
}

async function refreshVariableEditors() {
    await ensureVariableMeta();
    document.querySelectorAll(".var-insert-wrap").forEach((wrap) => {
        const targetId = wrap.querySelector(".var-insert-btn")?.dataset.varInsertTarget;
        if (!targetId) return;
        renderVarInsertMenu(wrap, targetId);
        const textarea = resolveVarTextarea(targetId);
        initVarEditorForTextarea(textarea);
        if (textarea) {
            updateVarValidationForTarget(targetId, textarea);
        }
    });
    document.querySelectorAll(".var-editor-wrap textarea, .var-editor-wrap input[type='text']").forEach((field) => {
        if (!field.closest(".var-insert-wrap")) {
            initVarEditorForTextarea(field);
        }
    });
}

function resolvePreviewDrawerContact() {
    const ctx = state.previewDrawer;
    if (ctx.contactId) {
        const contact = (state.contacts || []).find((item) => item.contactId === ctx.contactId);
        if (contact?.expertEmail) {
            return contact.expertEmail;
        }
    }
    if (ctx.expertEmail) {
        return ctx.expertEmail;
    }
    const orcidId = $("#previewOrcidInput")?.value?.trim() || ctx.orcidId || "";
    if (orcidId) {
        const contact = (state.contacts || []).find((item) => item.orcidId === orcidId);
        if (contact?.expertEmail) {
            return contact.expertEmail;
        }
    }
    return null;
}

function resolvePreviewDrawerSubject(targetId) {
    if (targetId === "qaRuleAnswerBody") {
        return null;
    }
    return null;
}

function highlightPreviewMailBody(rendered, variables) {
    let html = escapeHtml(rendered || "");
    const sorted = [...(variables || [])]
        .filter((item) => item.value)
        .sort((a, b) => b.value.length - a.value.length);
    sorted.forEach((item) => {
        const escapedValue = escapeHtml(item.value);
        if (!escapedValue || !html.includes(escapedValue)) {
            return;
        }
        const tagClass = item.usedFallback ? "preview-var-fallback-tag" : "preview-var-value-tag";
        html = html.replace(escapedValue, `<span class="${tagClass}">${escapedValue}</span>`);
    });
    return html;
}

function renderPreviewDrawerResult(result) {
    const toEl = $("#previewMailTo");
    const subjectEl = $("#previewMailSubject");
    const bodyEl = $("#previewMailBody");
    const rowsEl = $("#previewVarRows");
    const statEl = $("#previewVarStat");
    const errorEl = $("#previewDrawerError");
    if (errorEl) {
        errorEl.hidden = true;
        errorEl.textContent = "";
    }
    const toEmail = resolvePreviewDrawerContact();
    if (toEl) {
        toEl.textContent = toEmail || "—";
    }
    const subject = resolvePreviewDrawerSubject(state.previewDrawer.targetId);
    if (subjectEl) {
        subjectEl.textContent = subject || "—";
    }
    if (bodyEl) {
        bodyEl.innerHTML = highlightPreviewMailBody(result.rendered, result.variables);
    }
    const variables = result.variables || [];
    const invalidTokens = result.invalidTokens || [];
    let filledCount = 0;
    let fallbackCount = 0;
    variables.forEach((item) => {
        if (item.usedFallback) {
            fallbackCount += 1;
        } else if (item.filled) {
            filledCount += 1;
        } else {
            fallbackCount += 1;
        }
    });
    if (statEl) {
        statEl.textContent = `${filledCount} 有值 · ${fallbackCount} 兜底 · ${invalidTokens.length} 非法`;
    }
    if (rowsEl) {
        const variableRows = variables.map((item) => {
            const dotClass = item.usedFallback ? "fallback" : item.filled ? "filled" : "fallback";
            return `<div class="preview-var-row">
                <span class="preview-var-dot ${dotClass}"></span>
                <span class="preview-var-key">${escapeHtml(item.key)}</span>
                <span class="preview-var-label">${escapeHtml(item.label)}</span>
                <span class="preview-var-value" title="${escapeHtml(item.value || "")}">${escapeHtml(item.value || "—")}</span>
            </div>`;
        }).join("");
        const invalidRows = invalidTokens.map((token) => `<div class="preview-var-row">
                <span class="preview-var-dot invalid"></span>
                <span class="preview-var-key">${escapeHtml(token)}</span>
                <span class="preview-var-label">—</span>
                <span class="preview-var-value">白名单外，将原样发出</span>
            </div>`).join("");
        rowsEl.innerHTML = variableRows + invalidRows;
    }
}

function updatePreviewCoverage(matchCount, totalCount) {
    const coverageEl = $("#previewCoverage");
    if (!coverageEl) {
        return;
    }
    if (matchCount == null || totalCount == null || totalCount <= 0) {
        coverageEl.hidden = true;
        coverageEl.innerHTML = "";
        return;
    }
    const pct = Math.round((matchCount / totalCount) * 100);
    coverageEl.hidden = false;
    coverageEl.innerHTML = `满足全部占位符：<strong>${matchCount} / ${totalCount}</strong>（${pct}%）`;
}

function syncBodyScrollLock() {
    const drawerOpen = !$("#previewDrawer")?.hidden;
    const modalOpen = Array.from(document.querySelectorAll(".modal-shell")).some((el) => !el.hidden);
    if (drawerOpen || modalOpen) {
        document.body.classList.add("modal-open");
    } else {
        document.body.classList.remove("modal-open");
    }
}

function previewRailLabelForTarget(targetId) {
    return targetId === "qaRuleAnswerBody" ? "命中预览" : "邮件预览";
}

function shouldDockPreviewInComposeTemplate(targetId, composeModalHidden) {
    return targetId === "composeTemplate" && composeModalHidden === false;
}

function syncPreviewDrawerHost() {
    const shell = $("#previewDrawer");
    if (!shell) return;
    const composeModal = $("#composeTemplateModal");
    const composeSlot = $("#composeTemplatePreviewSlot");
    const dockInCompose = shouldDockPreviewInComposeTemplate(
        state.previewDrawer?.targetId,
        composeModal?.hidden !== false
    );
    if (dockInCompose && composeSlot && shell.parentElement !== composeSlot) {
        composeSlot.appendChild(shell);
    } else if (!dockInCompose && shell.parentElement !== document.body) {
        const rail = $("#previewRail");
        document.body.insertBefore(shell, rail || null);
    }
    document.body.classList.toggle("preview-compose-docked", dockInCompose && !shell.hidden);
}

function mountPreviewRail({ targetId, contactId, orcidId }) {
    const level = $("#previewScopeSel")?.value || "CANDIDATE";
    const mode = $("#previewModeSel")?.value || "SATISFY_ALL";
    state.previewDrawer = {
        targetId,
        contactId: contactId || null,
        orcidId: orcidId || null,
        level,
        mode,
        expertEmail: null,
        matchCount: null,
        totalCount: null,
        variantIndex: 0,
        variantPoolSize: 1
    };
    updatePreviewContextPanels();
    const orcidInput = $("#previewOrcidInput");
    if (orcidInput) {
        orcidInput.value = orcidId || "";
    }
    if ($("#previewScopeSel")) {
        $("#previewScopeSel").value = level;
    }
    if ($("#previewModeSel")) {
        $("#previewModeSel").value = mode;
    }
    const badge = $("#previewSourceBadge");
    if (badge) {
        badge.textContent = targetId === "composeTemplate" ? "服务端预览" : "变量渲染";
    }
    updatePreviewVariantSwitcher(1);
    const railLabel = $("#previewRailLabel");
    if (railLabel) {
        railLabel.textContent = previewRailLabelForTarget(targetId);
    }
    document.body.classList.add("preview-available");
    syncPreviewDrawerHost();
    const shell = $("#previewDrawer");
    if (!shell || shell.hidden) {
        document.body.classList.remove("preview-docked");
        shell?.classList.remove("open");
    }
}

function expandPreviewDrawer() {
    if (!state.previewDrawer?.targetId) {
        showStatus("预览未挂载", "error");
        return;
    }
    const shell = $("#previewDrawer");
    if (!shell) {
        return;
    }
    if (previewDrawerCollapseTimer != null) {
        window.clearTimeout(previewDrawerCollapseTimer);
        previewDrawerCollapseTimer = null;
    }
    document.body.classList.add("preview-available");
    syncPreviewDrawerHost();
    shell.hidden = false;
    document.body.classList.add("preview-docked");
    syncPreviewDrawerHost();
    requestAnimationFrame(() => shell.classList.add("open"));
    syncBodyScrollLock();
    refreshPreviewDrawer().catch((error) => showStatus(error.message, "error"));
}

function collapsePreviewDrawer() {
    const shell = $("#previewDrawer");
    if (!shell) {
        return;
    }
    if (previewDrawerCollapseTimer != null) {
        window.clearTimeout(previewDrawerCollapseTimer);
        previewDrawerCollapseTimer = null;
    }
    shell.classList.remove("open");
    document.body.classList.remove("preview-docked", "preview-compose-docked");
    shell.hidden = true;
    syncPreviewDrawerHost();
    syncBodyScrollLock();
}

function closePreviewDrawer() {
    if (previewDrawerCollapseTimer != null) {
        window.clearTimeout(previewDrawerCollapseTimer);
        previewDrawerCollapseTimer = null;
    }
    const shell = $("#previewDrawer");
    document.body.classList.remove("preview-available", "preview-docked", "preview-compose-docked");
    if (shell) {
        shell.classList.remove("open");
        shell.hidden = true;
    }
    if (state.previewDrawer) {
        state.previewDrawer.targetId = null;
    }
    syncPreviewDrawerHost();
    syncBodyScrollLock();
}

function openPreviewDrawer({ targetId, contactId, orcidId, skipContentCheck = false }) {
    if (targetId !== "composeTemplate") {
        const textarea = resolveVarTextarea(targetId);
        const text = textarea?.value || "";
        if (!skipContentCheck && !text.trim()) {
            showStatus("请先输入正文再预览", "error");
            return;
        }
    }
    mountPreviewRail({ targetId, contactId, orcidId });
    expandPreviewDrawer();
}

async function openComposeTemplatePreview() {
    await loadComposeTemplatePreviewOptions();
    if (state.previewDrawer.targetId !== "composeTemplate") {
        mountPreviewRail({ targetId: "composeTemplate" });
    }
    expandPreviewDrawer();
}

async function refreshPreviewDrawer() {
    if (isComposeTemplatePreviewTarget()) {
        await renderServerComposeTemplatePreview();
        return;
    }
    const textarea = resolveVarTextarea(state.previewDrawer.targetId);
    const text = textarea?.value || "";
    const orcidInput = $("#previewOrcidInput");
    const orcidId = orcidInput?.value?.trim() || state.previewDrawer.orcidId || "";
    const level = $("#previewScopeSel")?.value || state.previewDrawer.level || "CANDIDATE";
    state.previewDrawer.level = level;
    state.previewDrawer.mode = $("#previewModeSel")?.value || state.previewDrawer.mode || "SATISFY_ALL";
    updatePreviewVariantSwitcher(1);
    const payload = { text };
    if (state.previewDrawer.contactId) {
        payload.contactId = state.previewDrawer.contactId;
    } else if (orcidId) {
        payload.orcidId = orcidId;
        payload.level = level;
    } else {
        showStatus("请绑定专家或填写 ORCID 后再预览", "error");
        return;
    }
    const result = await api("/api/qa/render-preview", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    renderPreviewDrawerResult(result);
    const blockNotes = $("#previewComposeBlockNotes");
    const skipped = $("#previewComposeSkipped");
    if (blockNotes) {
        blockNotes.hidden = true;
        blockNotes.innerHTML = "";
    }
    if (skipped) {
        skipped.hidden = true;
        skipped.textContent = "";
    }
}

async function randomPreviewExpert() {
    const textarea = resolveVarTextarea(state.previewDrawer.targetId);
    const text = textarea?.value || "";
    const level = $("#previewScopeSel")?.value || "CANDIDATE";
    const mode = $("#previewModeSel")?.value || "SATISFY_ALL";
    state.previewDrawer.level = level;
    state.previewDrawer.mode = mode;
    const errorEl = $("#previewDrawerError");
    if (errorEl) {
        errorEl.hidden = true;
        errorEl.textContent = "";
    }
    const result = await api("/api/qa/preview/random-expert", {
        method: "POST",
        body: JSON.stringify({ text, level, mode })
    });
    if (result.error) {
        if (errorEl) {
            errorEl.hidden = false;
            errorEl.textContent = `随机抽样暂不可用：${result.error}`;
        }
        return;
    }
    state.previewDrawer.matchCount = result.matchCount;
    state.previewDrawer.totalCount = result.totalCount;
    updatePreviewCoverage(result.matchCount, result.totalCount);
    if (!result.expert) {
        if (errorEl) {
            errorEl.hidden = false;
            errorEl.textContent = "没有满足条件的专家";
        }
        return;
    }
    const orcidInput = $("#previewOrcidInput");
    if (orcidInput) {
        orcidInput.value = result.expert.orcidId || "";
    }
    state.previewDrawer.orcidId = result.expert.orcidId || null;
    state.previewDrawer.contactId = null;
    state.previewDrawer.expertEmail = result.expert.email || null;
    if ($("#previewScopeSel") && result.expert.indexLevel) {
        $("#previewScopeSel").value = result.expert.indexLevel;
        state.previewDrawer.level = result.expert.indexLevel;
    }
    await refreshPreviewDrawer();
}

function showQaRuleEditor() {
    $("#qaRuleModal").hidden = false;
    document.body.classList.add("modal-open");
    refreshVariableEditors().catch((error) => showStatus(error.message, "error"));
}

function hideQaRuleEditor() {
    const form = $("#qaRuleForm");
    form.reset();
    $("#qaRuleModal").hidden = true;
    document.body.classList.remove("modal-open");
    state.selectedRuleId = null;
    closePreviewDrawer();
}

function fillQaRuleForm(rule) {
    const form = $("#qaRuleForm");
    showQaRuleEditor();
    state.selectedRuleId = rule?.id || null;
    $("#qaRuleEditorTitle").textContent = rule
        ? `编辑事实：${rule.displayName || `#${rule.id}`}`
        : "新增 QA 事实";
    form.id.value = rule?.id || "";
    form.displayName.value = rule?.displayName || "";
    form.categoryId.value = rule?.categoryId || "";
    form.keywords.value = rule?.keywords || "";
    form.matchMode.value = rule?.matchMode || "ANY";
    form.priority.value = rule?.priority || 100;
    form.replyPolicy.value = rule?.replyPolicy || "REVIEW";
    form.answerBody.value = rule?.answerBody || rule?.replyBody || "";
    form.enabled.checked = rule?.enabled ?? true;
    const answerBodyEl = $("#qaRuleAnswerBody");
    if (answerBodyEl) {
        updateVarValidationForTarget("qaRuleAnswerBody", answerBodyEl);
    }
    mountPreviewRail({ targetId: "qaRuleAnswerBody" });
}

async function saveQaRule(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const answerBodyEl = $("#qaRuleAnswerBody");
    if (answerBodyEl && !updateVarValidationForTarget("qaRuleAnswerBody", answerBodyEl)) {
        showStatus("请先修正非法占位符", "error");
        return;
    }
    const values = formValues(form);
    const payload = {
        categoryId: numberValue(values.categoryId),
        keywords: values.keywords,
        matchMode: values.matchMode,
        priority: numberValue(values.priority, 100),
        answerBody: values.answerBody,
        replyPolicy: values.replyPolicy,
        displayName: values.displayName?.trim() || null,
        enabled: form.enabled.checked
    };
    const path = state.selectedRuleId ? `/api/qa/rules/${state.selectedRuleId}` : "/api/qa/rules";
    await api(path, { method: state.selectedRuleId ? "PUT" : "POST", body: JSON.stringify(payload) });
    showStatus("QA 事实已保存");
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

const replySnippetTypeLabels = {
    SALUTATION: "尊语",
    ACK: "致谢语",
    GREETING: "开场白",
    CLOSING: "结束语",
    CUSTOM: "自定义内容"
};

const replySnippetTypes = ["SALUTATION", "ACK", "GREETING", "CLOSING", "CUSTOM"];

async function loadReplySnippets() {
    state.replySnippets = await api("/api/reply-snippets");
    renderReplySnippetsPanels();
}

const aiTrainingSourceLabels = {
    MANUAL_IMPORT: "人工导入",
    AUTO_EXTRACTED: "自动提炼"
};

function switchAiTrainingTab(tab) {
    state.aiTraining.activeTab = tab;
    document.querySelectorAll("#view-ai-training .ai-tab").forEach((button) => {
        button.classList.toggle("active", button.dataset.tab === tab);
    });
    document.querySelectorAll("#view-ai-training .ai-tab-content").forEach((panel) => {
        const panelId = panel.id;
        const active = (tab === "qa" && panelId === "aiTabQa")
            || (tab === "dialogues" && panelId === "aiTabDialogues")
            || (tab === "prompts" && panelId === "aiTabPrompts")
            || (tab === "simulate" && panelId === "aiTabSimulate");
        panel.classList.toggle("active", active);
    });
}

function renderAiTrainingQaPager() {
    const pager = $("#aiTrainingQaPager");
    const size = state.aiTraining.qaSize;
    const total = state.aiTraining.qaTotal;
    const totalPages = Math.max(1, Math.ceil(total / size));
    if (total <= size) {
        pager.hidden = true;
        return;
    }
    pager.hidden = false;
    $("#aiTrainingQaPageInfo").textContent = `第 ${state.aiTraining.qaPage + 1} / ${totalPages} 页（共 ${total} 条）`;
    $("#aiTrainingQaPrevPage").disabled = state.aiTraining.qaPage <= 0;
    $("#aiTrainingQaNextPage").disabled = state.aiTraining.qaPage >= totalPages - 1;
}

function renderAiTrainingQaTable() {
    const rows = state.aiTraining.qaItems.map((item) => {
        const sourceLabel = aiTrainingSourceLabels[item.source] || item.source;
        const sourceBadgeClass = item.source === "AUTO_EXTRACTED" ? "ok" : "primary";
        return `
        <tr>
            <td><strong>${escapeHtml(item.topic)}</strong></td>
            <td>${badge(sourceLabel, sourceBadgeClass)}</td>
            <td class="muted-cell">${escapeHtml(item.keywords || "-")}</td>
            <td class="muted-cell">${escapeHtml((item.answer || "").slice(0, 240))}${(item.answer || "").length > 240 ? "…" : ""}</td>
            <td style="text-align: right; white-space: nowrap;">
                <button type="button" class="button small" data-action="edit-ai-training-qa" data-qa-id="${item.id}">编辑</button>
                <button type="button" class="button small" data-action="delete-ai-training-qa" data-qa-id="${item.id}">删除</button>
            </td>
        </tr>`;
    }).join("");
    $("#aiTrainingQaTable").innerHTML = rows
        || `<tr><td colspan="5" class="muted" style="text-align:center;padding:20px;">暂无知识库记录</td></tr>`;
    renderAiTrainingQaPager();
}

function renderAiTrainingDialogueTable() {
    const rows = (state.aiTraining.dialogueItems || []).map((item) => `
        <tr>
            <td><code>${escapeHtml(item.sourceRef)}</code></td>
            <td><strong>${escapeHtml(item.title)}</strong></td>
            <td class="muted-cell">${escapeHtml(item.keywords || "-")}</td>
            <td class="muted-cell">${item.turnCount}</td>
            <td>${badge(item.enabled ? "启用" : "停用", item.enabled ? "ok" : "warn")}</td>
        </tr>`).join("");
    $("#aiTrainingDialogueTable").innerHTML = rows
        || `<tr><td colspan="5" class="muted" style="text-align:center;padding:20px;">暂无对话范例</td></tr>`;
}

async function loadAiTrainingDialogues() {
    const items = await api("/api/ai-training/dialogues");
    state.aiTraining.dialogueItems = Array.isArray(items) ? items : [];
    renderAiTrainingDialogueTable();
}

function showAiTrainingQaModal() {
    $("#aiTrainingQaModal").hidden = false;
    document.body.classList.add("modal-open");
}

function hideAiTrainingQaModal() {
    const form = $("#aiTrainingQaForm");
    form?.reset();
    $("#aiTrainingQaModal").hidden = true;
    document.body.classList.remove("modal-open");
    state.aiTraining.editingQaId = null;
}

function showQaEditModal(qaItem) {
    const form = $("#aiTrainingQaForm");
    if (!form) return;
    state.aiTraining.editingQaId = qaItem?.id || null;
    $("#aiTrainingQaEditorTitle").textContent = qaItem ? "编辑 QA 条目" : "添加 QA 条目";
    form.topic.value = qaItem?.topic || "";
    form.question.value = qaItem?.question || "";
    form.answer.value = qaItem?.answer || "";
    form.keywords.value = qaItem?.keywords || "";
    showAiTrainingQaModal();
}

async function saveAiTrainingQaItem(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = formValues(form);
    const payload = {
        topic: values.topic,
        question: values.question || null,
        answer: values.answer,
        keywords: values.keywords || null
    };
    if (state.aiTraining.editingQaId) {
        await api(`/api/ai-training/qa/${state.aiTraining.editingQaId}`, {
            method: "PUT",
            body: JSON.stringify(payload)
        });
    } else {
        await api("/api/ai-training/qa", {
            method: "POST",
            body: JSON.stringify(payload)
        });
    }
    hideAiTrainingQaModal();
    await loadAiTrainingQa();
    showStatus("QA 条目已保存", "ok");
}

async function deleteQaItem(id) {
    if (!confirm("确认删除该 QA 条目？")) return;
    await api(`/api/ai-training/qa/${id}`, { method: "DELETE" });
    await loadAiTrainingQa();
    showStatus("QA 条目已删除", "ok");
}

async function loadAiTrainingQa() {
    const params = new URLSearchParams();
    params.set("page", String(state.aiTraining.qaPage));
    params.set("size", String(state.aiTraining.qaSize));
    if (state.aiTraining.qaSource) {
        params.set("source", state.aiTraining.qaSource);
    }
    const data = await api(`/api/ai-training/qa?${params}`);
    state.aiTraining.qaItems = data.items || [];
    state.aiTraining.qaTotal = data.total ?? state.aiTraining.qaItems.length;
    renderAiTrainingQaTable();
}

function fillAiTrainingPromptForm(config) {
    state.aiTraining.promptConfig = config;
    state.aiTraining.promptIsCustom = Boolean(config?.isCustom);
    $("#aiTrainingFreeFormPrompt").value = config?.freeFormSystemPrompt || "";
    $("#aiTrainingConstraints").value = config?.constraints || "";
    const statusEl = $("#aiTrainingPromptStatus");
    const infoEl = $("#aiTrainingPromptInfo");
    const restoreBtn = $("#aiTrainingRestoreDefaultBtn");
    if (statusEl) {
        statusEl.textContent = state.aiTraining.promptIsCustom
            ? "自定义提示词生效中"
            : "当前使用系统默认提示词";
    }
    if (infoEl) {
        infoEl.textContent = state.aiTraining.promptIsCustom
            ? "当前显示已保存的自定义提示词"
            : "当前显示系统生效提示词";
    }
    if (restoreBtn) {
        restoreBtn.hidden = !state.aiTraining.promptIsCustom;
    }
    $("#aiTrainingPromptUpdatedAt").textContent = config?.updatedAt
        ? `最近更新：${formatDateTime(config.updatedAt)}`
        : "";
}

async function loadAiTrainingPromptConfig() {
    const config = await api("/api/ai-training/prompt-config/effective");
    fillAiTrainingPromptForm(config);
}

async function restoreAiTrainingPromptDefault() {
    await api("/api/ai-training/prompt-config", {
        method: "PUT",
        body: JSON.stringify({ freeFormSystemPrompt: null, constraints: null })
    });
    await loadAiTrainingPromptConfig();
    showStatus("已恢复系统默认提示词", "ok");
}

function renderAiTrainingTagPills(containerId, options, selectedValue, valueKey) {
    const container = $(containerId);
    if (!container) return;
    const items = options || [];
    const allChip = `<button type="button" class="ai-training-tag-chip${selectedValue ? "" : " selected"}" data-value="">全部</button>`;
    const chips = items.map((item) => {
        const value = item[valueKey];
        const label = item.label || item.tag || value;
        const count = item.count != null ? ` (${item.count})` : "";
        const selected = selectedValue === value ? " selected" : "";
        return `<button type="button" class="ai-training-tag-chip${selected}" data-value="${escapeHtml(value)}">${escapeHtml(label)}${escapeHtml(count)}</button>`;
    }).join("");
    container.innerHTML = allChip + chips;
}

function mergeExpertTagAggregations(levelTagsList) {
    const byTag = new Map();
    (levelTagsList || []).forEach((tags) => {
        (tags || []).forEach((item) => {
            const tag = item?.tag;
            if (!tag) return;
            const existing = byTag.get(tag);
            const count = item.count || 0;
            if (existing) {
                existing.count += count;
            } else {
                byTag.set(tag, { tag, count });
            }
        });
    });
    return Array.from(byTag.values()).sort((a, b) => String(a.tag).localeCompare(String(b.tag)));
}

async function loadAiTrainingTagOptions() {
    const [candidateTags, applicationTags, inboundOptions] = await Promise.all([
        api("/api/experts/tags/aggregation?level=CANDIDATE"),
        api("/api/experts/tags/aggregation?level=APPLICATION"),
        api("/api/inbound-summary/tags/options")
    ]);
    state.aiTraining.expertTagOptions = mergeExpertTagAggregations([candidateTags, applicationTags]).map((item) => ({
        tag: item.tag,
        label: expertTagLabels[item.tag] || item.tag,
        count: item.count
    }));
    state.aiTraining.inboundTagOptions = inboundOptions.items || [];
    renderAiTrainingTagPills(
        "#aiTrainingExpertTagFilters",
        state.aiTraining.expertTagOptions,
        state.aiTraining.selectedExpertTag,
        "tag"
    );
    renderAiTrainingTagPills(
        "#aiTrainingInboundTagFilters",
        state.aiTraining.inboundTagOptions,
        state.aiTraining.selectedInboundTagKey,
        "tagKey"
    );
}

function renderAiTrainingSimulateMailPager() {
    const pager = $("#aiSimulateMailPager");
    const size = state.aiTraining.simulateMailsSize;
    const total = state.aiTraining.simulateMailsTotal;
    const totalPages = Math.max(1, Math.ceil(total / size));
    if (!pager) return;
    if (total <= size) {
        pager.hidden = true;
        return;
    }
    pager.hidden = false;
    $("#aiSimulateMailPageInfo").textContent = `第 ${state.aiTraining.simulateMailsPage + 1} / ${totalPages} 页（共 ${total} 条）`;
    $("#aiSimulateMailPrevPage").disabled = state.aiTraining.simulateMailsPage <= 0;
    $("#aiSimulateMailNextPage").disabled = state.aiTraining.simulateMailsPage >= totalPages - 1;
}

function renderAiTrainingMailList() {
    const container = $("#aiSimulateMailList");
    if (!container) return;
    const mails = state.aiTraining.simulateMails || [];
    if (mails.length === 0) {
        container.innerHTML = `<div class="ai-training-mail-empty muted">暂无匹配的来信记录</div>`;
        renderAiTrainingSimulateMailPager();
        return;
    }
    container.innerHTML = mails.map((mail) => {
        const selected = state.aiTraining.selectedSimulateMailContactId === mail.expertContactId ? " selected" : "";
        const timeStr = mail.receivedAt ? String(mail.receivedAt).replace("T", " ").slice(0, 19) : "-";
        const expertTags = (mail.expertTags || []).map((tag) =>
            `<span class="ai-training-tag-chip small">${escapeHtml(expertTagLabels[tag] || tag)}</span>`
        ).join("");
        const inboundTags = (mail.inboundTags || []).map((tag) =>
            `<span class="ai-training-tag-chip small inbound">${escapeHtml(tag.label)}</span>`
        ).join("");
        return `
            <button type="button" class="ai-training-mail-item${selected}" data-contact-id="${mail.expertContactId}">
                <div class="ai-training-mail-item-head">
                    <strong>${escapeHtml(mail.expertName || mail.expertEmail || "专家")}</strong>
                    <span class="muted">${escapeHtml(timeStr)}</span>
                </div>
                <div class="ai-training-mail-item-subject">${escapeHtml(mail.subject || "无主题")}</div>
                <div class="ai-training-mail-item-tags">${expertTags}${inboundTags}</div>
            </button>`;
    }).join("");
    renderAiTrainingSimulateMailPager();
}

function renderAiTrainingMailDetail(mail) {
    const detail = $("#aiSimulateMailDetail");
    if (!detail) return;
    if (!mail) {
        detail.classList.add("muted");
        detail.innerHTML = `
            <div class="ai-training-detail-empty">
                <svg viewBox="0 0 24 24" width="28" height="28" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
                <p>选择左侧邮件查看完整正文</p>
            </div>`;
        return;
    }
    detail.classList.remove("muted");
    const name = mail.expertName || mail.expertEmail || "专家";
    const initial = String(name).trim().charAt(0).toUpperCase() || "?";
    const timeStr = mail.receivedAt ? String(mail.receivedAt).replace("T", " ").slice(0, 19) : "";
    const expertTags = (mail.expertTags || []).map((tag) =>
        `<span class="ai-training-tag-chip small">${escapeHtml(expertTagLabels[tag] || tag)}</span>`
    ).join("");
    const inboundTags = (mail.inboundTags || []).map((tag) =>
        `<span class="ai-training-tag-chip small inbound">${escapeHtml(tag.label)}</span>`
    ).join("");
    detail.innerHTML = `
        <div class="ai-training-mail-detail-head">
            <div class="ai-training-detail-avatar">${escapeHtml(initial)}</div>
            <div class="ai-training-detail-titles">
                <div class="ai-training-detail-name-row">
                    <strong>${escapeHtml(name)}</strong>
                    ${timeStr ? `<span class="ai-training-detail-time">${escapeHtml(timeStr)}</span>` : ""}
                </div>
                <div class="ai-training-detail-email">${escapeHtml(mail.expertEmail || "")}</div>
            </div>
        </div>
        <div class="ai-training-detail-subject">${escapeHtml(mail.subject || "无主题")}</div>
        ${(expertTags || inboundTags) ? `<div class="ai-training-mail-item-tags">${expertTags}${inboundTags}</div>` : ""}
        <div class="ai-training-detail-body">
            ${translatableBody(mail.body || "", { emptyLabel: "无正文" })}
        </div>
    `;
}

function selectSimulateMail(mail) {
    state.aiTraining.selectedSimulateMailContactId = mail.expertContactId;
    state.aiTraining.selectedSimulateMailRecordId = mail.mailRecordId ?? null;
    state.aiTraining.selectedSimulateMail = mail;
    state.aiTraining.simulateResult = null;
    state.aiTraining.simulateRequestSeq += 1;
    const panel = $("#aiTrainingSimulateMessages")?.closest(".ai-chat-panel");
    setAiReplyLoading(panel, false);
    renderAiTrainingMailList();
    renderAiTrainingMailDetail(mail);
    $("#aiTrainingSimulateMessages").innerHTML = "";
    $("#aiTrainingSimulateMeta").innerHTML = "";
    renderAiReplyFeedback($("#aiTrainingSimulateFeedback"), null);
}

async function loadAiTrainingSimulateMails() {
    const params = new URLSearchParams();
    params.set("page", String(state.aiTraining.simulateMailsPage));
    params.set("size", String(state.aiTraining.simulateMailsSize));
    if (state.aiTraining.selectedExpertTag) {
        params.set("expertTag", state.aiTraining.selectedExpertTag);
    }
    if (state.aiTraining.selectedInboundTagKey) {
        params.set("inboundTagKey", state.aiTraining.selectedInboundTagKey);
    }
    const data = await api(`/api/ai-training/simulate/mails?${params}`);
    state.aiTraining.simulateMails = data.items || [];
    state.aiTraining.simulateMailsTotal = data.total ?? state.aiTraining.simulateMails.length;
    const selectedId = state.aiTraining.selectedSimulateMailContactId;
    const stillSelected = selectedId
        && state.aiTraining.simulateMails.some((mail) => mail.expertContactId === selectedId);
    if (!stillSelected) {
        state.aiTraining.selectedSimulateMailContactId = null;
        state.aiTraining.selectedSimulateMailRecordId = null;
        state.aiTraining.selectedSimulateMail = null;
        state.aiTraining.simulateResult = null;
        state.aiTraining.simulateRequestSeq += 1;
        const panel = $("#aiTrainingSimulateMessages")?.closest(".ai-chat-panel");
        setAiReplyLoading(panel, false);
        renderAiTrainingMailDetail(null);
        $("#aiTrainingSimulateMessages").innerHTML = "";
        $("#aiTrainingSimulateMeta").innerHTML = "";
        renderAiReplyFeedback($("#aiTrainingSimulateFeedback"), null);
    }
    renderAiTrainingMailList();
}

function renderAiTrainingSimulateResult(result) {
    state.aiTraining.simulateResult = result;
    const messages = $("#aiTrainingSimulateMessages");
    const meta = $("#aiTrainingSimulateMeta");
    const feedback = $("#aiTrainingSimulateFeedback");
    if (!result) {
        if (messages) messages.innerHTML = "";
        if (meta) meta.innerHTML = "";
        renderAiReplyFeedback(feedback, null);
        return;
    }
    renderAiReplyFeedback(feedback, result);
    if (messages) {
        messages.innerHTML = `
            <div class="ai-chat-bubble ai-chat-assistant ai-draft-bubble">
                <div class="ai-draft-head">
                    <span class="ai-draft-title">
                        <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z"/></svg>
                        AI 模拟草稿
                    </span>
                    <span class="ai-draft-badge">只读 · 不外发</span>
                    <button type="button" class="ai-draft-copy" data-action="copy-ai-draft" title="复制草稿">复制</button>
                </div>
                ${translatableBody(result.renderedDraftText || result.draftText || "(空草稿)")}
            </div>`;
        messages.scrollTop = 0;
    }
    if (meta) {
        const chips = [
            `模式 ${result.mode || "-"}`,
            result.generationState
                ? aiReplyGenerationStateLabel(result.generationState)
                : `LLM ${result.llmEnabled ? (result.usedLlm ? "已使用" : "未使用") : "已关闭"}`,
            `模型：${aiReplyModelLabel(result.selectedModel)}`
        ];
        const requestCount = Number(result.requestCount) || 0;
        if (requestCount > 0) {
            chips.push(`事实覆盖 ${Number(result.groundedRequestCount) || 0}/${requestCount}`);
        }
        const refBadges = (result.injectedDialogRefs || []).map((ref) => badge(`注入范例 ${ref}`, "info")).join(" ");
        meta.innerHTML = chips.map((chip) => `<span class="ai-meta-chip">${escapeHtml(chip)}</span>`).join("")
            + (refBadges ? ` ${refBadges}` : "");
    }
}

async function runAiTrainingSimulate() {
    const contactId = state.aiTraining.selectedSimulateMailContactId;
    if (!contactId) {
        showStatus("请先选择邮件", "warn");
        return;
    }
    const panel = $("#aiTrainingSimulateMessages")?.closest(".ai-chat-panel");
    if (!panel || panel.getAttribute("aria-busy") === "true") {
        return;
    }
    const promptOverride = $("#aiTrainingPromptOverride").value.trim();
    const mailRecordId = state.aiTraining.selectedSimulateMailRecordId;
    const requestSeq = state.aiTraining.simulateRequestSeq;
    const expectedModel = readAiReplyModelSelection("#aiTrainingReplyModel", state.aiTraining.simulateModel);
    state.aiTraining.simulateModel = expectedModel;
    const feedback = $("#aiTrainingSimulateFeedback");
    renderAiReplyFeedback(feedback, null);
    setAiReplyLoading(panel, true);
    try {
        const body = {
            expertContactId: contactId,
            promptOverride: promptOverride || null,
            model: expectedModel
        };
        if (mailRecordId != null) {
            body.mailRecordId = mailRecordId;
        }
        const result = await api("/api/ai-training/simulate", {
            method: "POST",
            body: JSON.stringify(body)
        });
        const currentModel = readAiReplyModelSelection("#aiTrainingReplyModel", state.aiTraining.simulateModel);
        const stillCurrent = requestSeq === state.aiTraining.simulateRequestSeq
            && contactId === state.aiTraining.selectedSimulateMailContactId
            && mailRecordId === state.aiTraining.selectedSimulateMailRecordId
            && expectedModel === currentModel;
        if (!stillCurrent) {
            return;
        }
        if (result.selectedModel !== expectedModel) {
            renderAiReplyFeedback(feedback, null, "模型响应与当前选择不一致，请重新生成");
            return;
        }
        renderAiTrainingSimulateResult(result);
        showStatus("模拟回复已生成（未外发）", "ok");
    } catch (error) {
        const currentModel = readAiReplyModelSelection("#aiTrainingReplyModel", state.aiTraining.simulateModel);
        const stillCurrent = requestSeq === state.aiTraining.simulateRequestSeq
            && contactId === state.aiTraining.selectedSimulateMailContactId
            && mailRecordId === state.aiTraining.selectedSimulateMailRecordId
            && expectedModel === currentModel;
        if (stillCurrent) {
            renderAiReplyFeedback(feedback, null, error.message || "未知错误");
        }
        throw error;
    } finally {
        const currentModel = readAiReplyModelSelection("#aiTrainingReplyModel", state.aiTraining.simulateModel);
        const stillCurrent = requestSeq === state.aiTraining.simulateRequestSeq
            && contactId === state.aiTraining.selectedSimulateMailContactId
            && mailRecordId === state.aiTraining.selectedSimulateMailRecordId
            && expectedModel === currentModel;
        if (stillCurrent) {
            setAiReplyLoading(panel, false);
        }
    }
}

async function saveAiTrainingPromptConfig(event) {
    event.preventDefault();
    const payload = {
        freeFormSystemPrompt: $("#aiTrainingFreeFormPrompt").value.trim() || null,
        constraints: $("#aiTrainingConstraints").value.trim() || null
    };
    await api("/api/ai-training/prompt-config", {
        method: "PUT",
        body: JSON.stringify(payload)
    });
    await loadAiTrainingPromptConfig();
    showStatus("提示词配置已保存", "ok");
}

async function loadAiTraining() {
    await Promise.all([
        loadAiTrainingQa(),
        loadAiTrainingDialogues(),
        loadAiTrainingPromptConfig(),
        loadAiTrainingTagOptions(),
        loadAiTrainingSimulateMails()
    ]);
}

function renderReplySnippetRow(snippet, showDefault) {
    const defaultCell = showDefault
        ? `<td>${snippet.isDefault ? badge("默认", "ok") : ""}</td>`
        : "";
    const defaultAction = showDefault && !snippet.isDefault
        ? `<button class="button" data-action="reply-snippet-default" data-id="${snippet.id}">设默认</button>`
        : "";
    const variantCount = (snippet.variants || []).length;
    const variantCell = variantCount > 0
        ? `<td>${badge(`${variantCount} 变体`, "primary")}</td>`
        : "<td></td>";
    return `
        <tr>
            <td class="muted-cell">${escapeHtml((snippet.content || "").slice(0, 120))}</td>
            <td>${snippet.displayOrder}</td>
            ${variantCell}
            ${defaultCell}
            <td>${badge(snippet.enabled ? "启用" : "禁用", snippet.enabled ? "ok" : "error")}</td>
            <td class="actions">
                <button class="button" data-action="edit-reply-snippet" data-id="${snippet.id}">编辑</button>
                ${defaultAction}
                <button class="button" data-action="toggle-reply-snippet" data-id="${snippet.id}" data-enabled="${snippet.enabled}">
                    ${snippet.enabled ? "禁用" : "启用"}
                </button>
                <button class="button" data-action="delete-reply-snippet" data-id="${snippet.id}">删除</button>
            </td>
        </tr>`;
}

function renderReplySnippetTypePanel(type, snippets) {
    const showDefault = type !== "ACK" && type !== "CUSTOM";
    const defaultHeader = showDefault ? "<th>默认</th>" : "";
    const rows = snippets
        .map((snippet) => renderReplySnippetRow(snippet, showDefault))
        .join("") || `<tr><td colspan="${showDefault ? 6 : 5}" class="muted" style="text-align:center;padding:20px;">暂无片段</td></tr>`;
    return `
        <section class="panel" style="margin-bottom:16px;">
            <div class="panel-head">
                <h2>${replySnippetTypeLabels[type] || type}</h2>
            </div>
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>内容</th>
                            <th>排序</th>
                            <th>变体</th>
                            ${defaultHeader}
                            <th>状态</th>
                            <th style="text-align: right;">操作</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        </section>`;
}

function renderReplySnippetsPanels() {
    const container = $("#replySnippetsPanels");
    if (!container) return;
    const byType = Object.fromEntries(replySnippetTypes.map((type) => [type, []]));
    (state.replySnippets || []).forEach((snippet) => {
        if (byType[snippet.snippetType]) {
            byType[snippet.snippetType].push(snippet);
        }
    });
    container.innerHTML = replySnippetTypes
        .map((type) => renderReplySnippetTypePanel(type, byType[type]))
        .join("");
}

function showReplySnippetEditor() {
    $("#replySnippetModal").hidden = false;
    document.body.classList.add("modal-open");
    refreshVariableEditors().catch((error) => showStatus(error.message, "error"));
}

function hideReplySnippetEditor() {
    const form = $("#replySnippetForm");
    form.reset();
    renderContentVariantRows($("#replySnippetVariantsContainer"), []);
    $("#replySnippetModal").hidden = true;
    document.body.classList.remove("modal-open");
    state.selectedReplySnippetId = null;
    updateReplySnippetDefaultFieldVisibility();
    closePreviewDrawer();
}

function updateReplySnippetDefaultFieldVisibility() {
    const type = $("#replySnippetForm")?.snippetType?.value || "";
    const defaultRow = $("#replySnippetDefaultRow");
    if (defaultRow) {
        defaultRow.hidden = type === "ACK" || type === "CUSTOM";
    }
}

function fillReplySnippetForm(snippet, presetType) {
    const form = $("#replySnippetForm");
    showReplySnippetEditor();
    state.selectedReplySnippetId = snippet?.id || null;
    $("#replySnippetEditorTitle").textContent = snippet
        ? `编辑片段：${replySnippetTypeLabels[snippet.snippetType] || snippet.snippetType}`
        : "新建回复片段";
    form.id.value = snippet?.id || "";
    form.snippetType.value = snippet?.snippetType || presetType || "SALUTATION";
    form.snippetType.disabled = Boolean(snippet?.id);
    form.content.value = snippet?.content || "";
    form.displayOrder.value = snippet?.displayOrder ?? 100;
    form.enabled.checked = snippet?.enabled ?? true;
    form.isDefault.checked = snippet?.isDefault ?? false;
    renderContentVariantRows($("#replySnippetVariantsContainer"), snippet?.variants || []);
    updateReplySnippetDefaultFieldVisibility();
    const contentEl = $("#replySnippetContent");
    if (contentEl) {
        updateVarValidationForTarget("replySnippetContent", contentEl);
    }
    mountPreviewRail({ targetId: "replySnippetContent" });
}

async function saveReplySnippet(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const contentEl = $("#replySnippetContent");
    if (contentEl && !updateVarValidationForTarget("replySnippetContent", contentEl)) {
        showStatus("请先修正非法占位符", "error");
        return;
    }
    const variantsContainer = $("#replySnippetVariantsContainer");
    const mainText = contentEl?.value || "";
    if (!validateContentVariantInputs(variantsContainer, mainText)) {
        return;
    }
    const values = formValues(form);
    const payload = {
        content: values.content,
        displayOrder: numberValue(values.displayOrder, 100),
        isDefault: form.isDefault.checked,
        enabled: form.enabled.checked,
        variants: collectContentVariants(variantsContainer)
    };
    if (state.selectedReplySnippetId) {
        await api(`/api/reply-snippets/${state.selectedReplySnippetId}`, {
            method: "PUT",
            body: JSON.stringify(payload)
        });
    } else {
        await api("/api/reply-snippets", {
            method: "POST",
            body: JSON.stringify({
                snippetType: values.snippetType,
                ...payload
            })
        });
    }
    showStatus("回复片段已保存");
    hideReplySnippetEditor();
    await loadReplySnippets();
}

async function handleReplySnippetAction(button) {
    const action = button.dataset.action;
    const id = button.dataset.id;
    if (action === "edit-reply-snippet") {
        const snippet = state.replySnippets.find((item) => String(item.id) === String(id));
        fillReplySnippetForm(snippet);
        return;
    }
    if (action === "toggle-reply-snippet") {
        const enabled = button.dataset.enabled === "true";
        await api(`/api/reply-snippets/${id}/${enabled ? "disable" : "enable"}`, { method: "POST" });
        await loadReplySnippets();
        return;
    }
    if (action === "reply-snippet-default") {
        await api(`/api/reply-snippets/${id}/default`, { method: "POST" });
        showStatus("已设为默认片段", "ok");
        await loadReplySnippets();
        return;
    }
    if (action === "delete-reply-snippet") {
        if (!confirm("确认删除该片段？")) return;
        await api(`/api/reply-snippets/${id}`, { method: "DELETE" });
        showStatus("片段已删除", "ok");
        await loadReplySnippets();
    }
}

const suppressionSourceLabels = {
    INBOUND_REPLY: "入站回复",
    ONE_CLICK: "一键退订",
    MAILTO: "Mailto",
    MANUAL: "手动添加"
};

function renderSuppressionPager(size) {
    const pager = $("#suppressionPager");
    const total = state.suppressionsTotal;
    const totalPages = Math.max(1, Math.ceil(total / size));
    if (total <= size) {
        pager.hidden = true;
        return;
    }
    pager.hidden = false;
    $("#suppressionPageInfo").textContent = `第 ${state.suppressionsPage + 1} / ${totalPages} 页（共 ${total} 条）`;
    $("#suppressionPrevPage").disabled = state.suppressionsPage <= 0;
    $("#suppressionNextPage").disabled = state.suppressionsPage >= totalPages - 1;
}

async function loadSuppressions() {
    const size = 50;
    const params = new URLSearchParams();
    params.set("page", String(state.suppressionsPage));
    params.set("size", String(size));
    if (state.suppressionKeyword) {
        params.set("keyword", state.suppressionKeyword);
    }
    const data = await api(`/api/suppressions?${params}`);
    state.suppressions = data.items || [];
    state.suppressionsTotal = data.total ?? state.suppressions.length;
    renderSuppressionsTable();
    renderSuppressionPager(size);
}

function renderSuppressionsTable() {
    $("#suppressionsTable").innerHTML = state.suppressions.map((item) => {
        const sourceLabel = suppressionSourceLabels[item.source] || item.source;
        return `
        <tr>
            <td><strong>${escapeHtml(item.email)}</strong></td>
            <td>${badge(sourceLabel, item.source === "MANUAL" ? "warn" : "neutral")}</td>
            <td class="muted-cell">${escapeHtml(item.reason || "-")}</td>
            <td>${escapeHtml(formatDateTime(item.createdAt) || "-")}</td>
            <td class="actions">
                <button class="button" data-action="remove-suppression" data-email="${escapeHtml(item.email)}">移除</button>
            </td>
        </tr>
    `;
    }).join("") || `<tr><td colspan="5" class="muted" style="text-align:center; padding:20px;">暂无退订记录</td></tr>`;
}

function showSuppressionEditor() {
    $("#suppressionModal").hidden = false;
    document.body.classList.add("modal-open");
}

function hideSuppressionEditor() {
    const form = $("#suppressionForm");
    form.reset();
    $("#suppressionModal").hidden = true;
    document.body.classList.remove("modal-open");
}

async function saveSuppression(event) {
    event.preventDefault();
    const values = formValues(event.currentTarget);
    const payload = {
        email: values.email,
        reason: values.reason?.trim() || null
    };
    const result = await api("/api/suppressions", { method: "POST", body: JSON.stringify(payload) });
    showStatus(result.added ? "已加入退订名单" : "该邮箱已在名单中", result.added ? "ok" : "warn");
    hideSuppressionEditor();
    await loadSuppressions();
}

async function handleSuppressionAction(button) {
    const action = button.dataset.action;
    const email = button.dataset.email;
    if (action === "remove-suppression") {
        const result = await api(`/api/suppressions?email=${encodeURIComponent(email)}`, { method: "DELETE" });
        showStatus(result.removed ? "已从退订名单移除" : "该邮箱不在名单中", result.removed ? "ok" : "warn");
        await loadSuppressions();
    }
}

const expertTagLabels = {
    auto_promoted: "自动晋升",
    verified: "已验证",
    discovered: "新发现",
    "承诺回复材料": "承诺回复材料",
    "重点关注": "重点关注",
    "待补充信息": "待补充信息"
};

const EXPERT_PRESET_TAGS = ["承诺回复材料", "重点关注", "待补充信息"];

const expertTagColors = {
    auto_promoted: "#3b82f6",
    verified: "#22c55e",
    discovered: "#f59e0b"
};

function renderContactListSkeleton() {
    $("#contactList").innerHTML = Array.from({ length: 6 }, () => `
        <div class="list-item skeleton-item">
            <div class="skeleton-line" style="width: 55%;"></div>
            <div class="skeleton-line" style="width: 85%;"></div>
            <div class="skeleton-line" style="width: 40%;"></div>
        </div>
    `).join("");
    $("#contactPager").hidden = true;
}

function renderContactPager(size) {
    const pager = $("#contactPager");
    const totalHits = state.contactsTotalHits;
    let totalPages = Math.max(1, Math.ceil(totalHits / size));
    // ES max_result_window 限制: from+size 不能超过 10000，深分页页码截断
    const maxPages = Math.floor(10000 / size);
    const capped = totalPages > maxPages;
    if (capped) totalPages = maxPages;
    if (totalHits <= size) {
        pager.hidden = true;
        return;
    }
    pager.hidden = false;
    $("#contactPageInfo").textContent =
        `第 ${state.contactsPage + 1} / ${totalPages} 页${capped ? "（仅可翻前 1 万条）" : ""}`;
    $("#contactPrevPage").disabled = state.contactsPage <= 0;
    $("#contactNextPage").disabled = state.contactsPage >= totalPages - 1;
}

async function loadEmailProviders(level, { filters = {}, refreshConfigDropdown = false } = {}) {
    try {
        const params = new URLSearchParams({ level });
        if (filters.tag) params.set("tag", filters.tag);
        if (filters.operatorStatus) params.set("operatorStatus", filters.operatorStatus);
        if (filters.region) params.set("region", filters.region);

        const filterDropdown = $("#expertEmailDomainFilter");
        const configDropdown = $("#batchSendEmailDomain");

        const currentFilterVal = filterDropdown ? filterDropdown.value : "";
        const currentConfigVal = configDropdown ? configDropdown.value : "";

        const [domains, configDomains] = await Promise.all([
            api(`/api/experts/email-providers?${params}`),
            refreshConfigDropdown
                ? api(`/api/experts/email-providers?level=${encodeURIComponent(level)}`)
                : Promise.resolve(null)
        ]);

        if (filterDropdown) {
            filterDropdown.innerHTML = '<option value="">全部服务商</option>';
            domains.forEach(d => {
                const opt = document.createElement("option");
                opt.value = d.domain;
                opt.textContent = `${d.domain} (${d.count})`;
                filterDropdown.appendChild(opt);
            });
            filterDropdown.value = currentFilterVal;
            if (filterDropdown.value !== currentFilterVal) {
                filterDropdown.value = "";
            }
        }

        if (refreshConfigDropdown && configDropdown) {
            configDropdown.innerHTML = '<option value="">全部</option>';
            (configDomains || []).forEach(d => {
                const opt = document.createElement("option");
                opt.value = d.domain;
                opt.textContent = `${d.domain} (${d.count})`;
                configDropdown.appendChild(opt);
            });
            configDropdown.value = currentConfigVal;
            if (configDropdown.value !== currentConfigVal) {
                configDropdown.value = "";
            }
        }
    } catch (e) {
        console.error("Failed to load email providers:", e);
    }
}

async function loadRegions(level, { filters = {} } = {}) {
    try {
        const params = new URLSearchParams({ level });
        if (filters.tag) params.set("tag", filters.tag);
        if (filters.operatorStatus) params.set("operatorStatus", filters.operatorStatus);
        if (filters.emailDomain) params.set("emailDomain", filters.emailDomain);
        const regions = await api(`/api/experts/regions?${params}`);
        const filterDropdown = $("#expertRegionFilter");
        const currentFilterVal = filterDropdown ? filterDropdown.value : "";

        if (filterDropdown) {
            filterDropdown.innerHTML = '<option value="">全部地区</option>';
            regions.forEach(d => {
                const opt = document.createElement("option");
                opt.value = d.region;
                opt.textContent = `${d.region} (${d.count})`;
                filterDropdown.appendChild(opt);
            });
            filterDropdown.value = currentFilterVal;
            if (filterDropdown.value !== currentFilterVal) {
                filterDropdown.value = "";
            }
        }
    } catch (e) {
        console.error("Failed to load regions:", e);
    }
}

async function loadExpertTagOptions(level, { filters = {} } = {}) {
    try {
        const params = new URLSearchParams({ level });
        if (filters.operatorStatus) params.set("operatorStatus", filters.operatorStatus);
        if (filters.emailDomain) params.set("emailDomain", filters.emailDomain);
        if (filters.region) params.set("region", filters.region);
        const tags = await api(`/api/experts/tags/aggregation?${params}`);
        const filterDropdown = $("#expertTagFilter");
        const currentFilterVal = filterDropdown ? filterDropdown.value : "";

        if (filterDropdown) {
            filterDropdown.innerHTML = '<option value="">全部标签</option>';
            tags.forEach(item => {
                const opt = document.createElement("option");
                opt.value = item.tag;
                const label = expertTagLabels[item.tag] || item.tag;
                opt.textContent = `${label} (${item.count})`;
                filterDropdown.appendChild(opt);
            });
            filterDropdown.value = currentFilterVal;
            if (filterDropdown.value !== currentFilterVal) {
                filterDropdown.value = "";
            }
        }
    } catch (e) {
        console.error("Failed to load expert tags:", e);
    }
}

function renderExpertTagEditor(tags, orcidId, level, editorId = "expertTagEditor") {
    const chips = (tags || []).map(tag => `
        <span class="expert-tag tag-${escapeHtml(tag)}">
            ${escapeHtml(expertTagLabels[tag] || tag)}
            <button type="button" class="expert-tag-remove" data-action="expert-remove-tag" data-tag="${escapeHtml(tag)}" title="删除标签">×</button>
        </span>
    `).join("") || `<span class="muted">暂无标签</span>`;
    return `
        <div class="detail-section expert-tag-editor" id="${escapeHtml(editorId)}" data-orcid="${escapeHtml(orcidId)}" data-level="${escapeHtml(level)}">
            <div class="inbound-tag-editor-head">
                <h3>专家标签</h3>
                <div class="inbound-tag-editor-actions">
                    <button type="button" class="button primary small" data-action="expert-add-tag-open">+ 添加标签</button>
                </div>
            </div>
            <div class="inbound-tag-editor-chips">${chips}</div>
        </div>
    `;
}

function openExpertTagAddDialog(existingTags = []) {
    return new Promise((resolve) => {
        const dialog = document.getElementById("actionDialog");
        const form = document.getElementById("actionDialogForm");
        const titleEl = document.getElementById("actionDialogTitle");
        const bodyEl = document.getElementById("actionDialogBody");
        titleEl.textContent = "添加专家标签";
        const availablePresets = EXPERT_PRESET_TAGS.filter(tag => !(existingTags || []).includes(tag));
        bodyEl.innerHTML = `
            <div class="form-group" style="margin-bottom: 12px; display: flex; flex-direction: column;">
                <label style="font-weight: bold; margin-bottom: 4px;">预设标签</label>
                <select id="expertPresetTag" class="input" style="width: 100%; box-sizing: border-box;">
                    <option value="">选择预设...</option>
                    ${availablePresets.map(tag => `<option value="${escapeHtml(tag)}">${escapeHtml(expertTagLabels[tag] || tag)}</option>`).join("")}
                </select>
            </div>
            <div class="form-group" style="margin-bottom: 12px; display: flex; flex-direction: column;">
                <label style="font-weight: bold; margin-bottom: 4px;">或自定义标签</label>
                <input type="text" id="expertCustomTag" class="input" placeholder="输入自定义标签" style="width: 100%; box-sizing: border-box;">
            </div>
        `;

        const handleCancel = () => {
            cleanup();
            resolve(null);
        };
        const handleSubmit = (e) => {
            e.preventDefault();
            const preset = document.getElementById("expertPresetTag")?.value?.trim() || "";
            const custom = document.getElementById("expertCustomTag")?.value?.trim() || "";
            const tag = custom || preset;
            cleanup();
            resolve(tag || null);
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

async function fetchExpertTagsFromEs(orcidId, level) {
    if (!orcidId) return [];
    const params = new URLSearchParams({ orcidId, level });
    const profile = await api(`/api/experts/profile?${params}`);
    return profile.tags || [];
}

async function refreshExpertTagsFromEs(orcidId, level) {
    return fetchExpertTagsFromEs(orcidId, level);
}

async function mutateExpertTag(orcidId, level, tag, action) {
    const endpoint = action === "add" ? "/api/experts/tags/add" : "/api/experts/tags/remove";
    const result = await api(endpoint, {
        method: "POST",
        body: JSON.stringify({ orcidId, tag, level })
    });
    if (!result.success) {
        throw new Error(result.message || "标签操作失败");
    }
    await loadContacts();
    await loadExpertTagOptions(level, {
        filters: {
            operatorStatus: $("#contactStatusFilter")?.value || "",
            emailDomain: $("#expertEmailDomainFilter")?.value || "",
            region: $("#expertRegionFilter")?.value || ""
        }
    });
    const refreshedTags = await refreshExpertTagsFromEs(orcidId, level);
    if (action === "add") {
        return refreshedTags.includes(tag) ? refreshedTags : [...refreshedTags, tag];
    }
    return refreshedTags.filter((item) => item !== tag);
}

function updateExpertTagEditor(orcidId, tags, level, editorId = "expertTagEditor") {
    const editor = document.getElementById(editorId);
    if (!editor || editor.dataset.orcid !== orcidId) return;
    editor.outerHTML = renderExpertTagEditor(tags, orcidId, level, editorId);
}

function setTagEditorLoading(editor, loading, message = "处理中...") {
    if (!editor) return;
    editor.classList.toggle("tag-editor-loading", loading);
    editor.setAttribute("aria-busy", loading ? "true" : "false");
    editor.querySelectorAll("button").forEach((button) => {
        button.disabled = loading;
    });
    let overlay = editor.querySelector(":scope > .tag-editor-loading-overlay");
    if (loading) {
        if (!overlay) {
            overlay = document.createElement("div");
            overlay.className = "tag-editor-loading-overlay";
            overlay.innerHTML = `<span class="tag-editor-spinner"></span><span class="tag-editor-loading-text"></span>`;
            editor.appendChild(overlay);
        }
        overlay.querySelector(".tag-editor-loading-text").textContent = message;
    } else if (overlay) {
        overlay.remove();
    }
}

const AI_REPLY_WARNING_LABELS = {
    EXPERT_PROFILE_NOT_FOUND: "未找到现有专家画像，本次回复未引用研究资料。",
    EXPERT_RESEARCH_CONTEXT_INSUFFICIENT: "现有专家研究资料不足，匹配度问题需要人工确认或先使用已有资料补充功能。",
    UNAUTHORIZED_ACTION_REMOVED: "已移除未授权的外发动作请求。",
    AI_REPLY_PREVIEW_ACCOUNT_NOT_FOUND: "无法确定回信账号，变量预览未完全渲染",
    AI_REPLY_PREVIEW_INVALID_PLACEHOLDER: "草稿含未知变量占位符，已保留原文",
    AI_REPLY_STRUCTURED_RESPONSE_INVALID: "模型返回格式无效，已使用审核依据生成结构化草稿。",
    AI_REPLY_LLM_TOTAL_TIMEOUT: "DeepSeek 生成总时限已用尽，已回退为人工审核草稿。",
    AI_REPLY_CLAIM_HALLUCINATED_FACT: "文本含未经审核的数字或链接，请依据 QA 事实手动核对。",
    AI_REPLY_CLAIM_MODALITY_STRENGTHENED: "承诺语气超出依据——原文含条件性表述（如 may/can），但正文强化为保证性表述。",
    AI_REPLY_CLAIM_HIGH_RISK_UNBACKED: "正文含高风险声明，但依据中不含该声明。",
    AI_REPLY_CLAIM_SOURCE_UNAVAILABLE: "审核依据不可用，请重新选择事实或核对规则状态。",
    AI_REPLY_CLAIM_TRUST_RHETORIC: "正文含信任替代话术（如\"请放心\"/\"trust us\"），请改用依据中的事实陈述。",
    AI_REPLY_CLAIM_CONFIDENTIALITY_SUBSTITUTE: "正文以\"保密\"代替具体事实，请提供在审核依据中能找到的具体信息。",
    AI_REPLY_CLAIM_ROLE_DISCLOSURE_OMITTED: "依据中明确有\"服务方\"等角色披露要求，但正文未披露。请补上角色身份说明。",
    AI_REPLY_CLAIM_ENTERPRISE_UNGROUNDED: "正文给企业确定性描述，但依据中为不确定性表述。请改用依据原文。",
    AI_REPLY_ACTION_SENSITIVE_MATERIAL: "正文含敏感材料请求（护照/身份证/银行证明），已标记为风险。",
    AI_REPLY_ACTION_CV_PURPOSE_MISSING: "请求简历但未说明用途，请补充资格审核/研究方向匹配等说明。",
    AI_REPLY_ACTION_CV_OPTIONALITY_MISSING: "请求简历但未说明自愿性，请补充\"如您方便\"等提示。",
    AI_REPLY_PREFLIGHT_SOURCE_CHANGED: "依据已变化或不可用，请重新生成草稿或重新选择事实。",
    AI_REPLY_PREFLIGHT_NO_EVIDENCE: "当前正文无关联审核事实，部分检查已跳过。"
};

const PREFLIGHT_PASS_TEXT = "当前未发现新增风险，发送前仍请人工核对";
const PREFLIGHT_UNAVAILABLE_TEXT = "复验暂不可用，请人工核对";

function shortEvidenceHash(version) {
    return (version || "").substring(0, 12);
}

function aiReplyGenerationStateLabel(state) {
    switch (state) {
        case "LLM_USED":
            return "模型已生成";
        case "FALLBACK_LLM_DISABLED":
            return "LLM 已关闭—结构化规则草稿";
        case "FALLBACK_CLIENT_UNAVAILABLE":
            return "模型客户端不可用—结构化规则草稿";
        case "FALLBACK_NO_RESPONSE":
            return "模型无有效响应—结构化规则草稿";
        default:
            return "";
    }
}

const AI_REPLY_FAILURE_WARNING_CODES = new Set([
    "AI_REPLY_LLM_TIMEOUT",
    "AI_REPLY_LLM_TOTAL_TIMEOUT",
    "AI_REPLY_LLM_RATE_LIMITED",
    "AI_REPLY_LLM_NETWORK_ERROR",
    "AI_REPLY_LLM_PROVIDER_ERROR",
    "AI_REPLY_LLM_EMPTY_RESPONSE",
    "AI_REPLY_TRUST_REPAIR_EXHAUSTED"
]);

function isAiReplyGenerationSuccess(result) {
    if (!result) return false;
    return result.usedLlm === true && result.generationState === "LLM_USED";
}

function aiReplyFailureReasonLabel(code) {
    switch (code) {
        case "AI_REPLY_LLM_TIMEOUT":
            return "DeepSeek 请求超时";
        case "AI_REPLY_LLM_TOTAL_TIMEOUT":
            return "DeepSeek 生成总时限已用尽";
        case "AI_REPLY_LLM_RATE_LIMITED":
            return "DeepSeek 请求过于频繁";
        case "AI_REPLY_LLM_NETWORK_ERROR":
            return "无法连接 DeepSeek";
        case "AI_REPLY_LLM_PROVIDER_ERROR":
            return "DeepSeek 服务异常";
        case "AI_REPLY_LLM_EMPTY_RESPONSE":
            return "DeepSeek 返回空内容";
        case "AI_REPLY_TRUST_REPAIR_EXHAUSTED":
            return "DeepSeek 返回内容未通过结构与可信边界校验";
        default:
            return "DeepSeek 未返回有效内容";
    }
}

function resolveAiReplyFailureReason(contextWarnings) {
    if (!Array.isArray(contextWarnings)) return null;
    var priority = [
        "AI_REPLY_LLM_TOTAL_TIMEOUT",
        "AI_REPLY_LLM_TIMEOUT",
        "AI_REPLY_LLM_RATE_LIMITED",
        "AI_REPLY_LLM_NETWORK_ERROR",
        "AI_REPLY_LLM_PROVIDER_ERROR",
        "AI_REPLY_LLM_EMPTY_RESPONSE",
        "AI_REPLY_TRUST_REPAIR_EXHAUSTED"
    ];
    for (var i = 0; i < priority.length; i++) {
        var code = priority[i];
        if (AI_REPLY_FAILURE_WARNING_CODES.has(code) && contextWarnings.includes(code)) {
            return code;
        }
    }
    return null;
}

function resolveAiReplyFailureReasonFromResult(result) {
    if (!result) return null;
    var warnings = Array.isArray(result.contextWarnings) ? result.contextWarnings : [];
    var warnReason = resolveAiReplyFailureReason(warnings);
    if (warnReason) return warnReason;
    if (!result.usedLlm || result.generationState !== "LLM_USED") {
        if (result.generationState === "FALLBACK_LLM_DISABLED") return "FALLBACK_LLM_DISABLED";
        if (result.generationState === "FALLBACK_CLIENT_UNAVAILABLE") return "FALLBACK_CLIENT_UNAVAILABLE";
        if (result.generationState === "FALLBACK_NO_RESPONSE") return "FALLBACK_NO_RESPONSE";
    }
    return null;
}

function resolveFactDisplayName(ruleId, evidenceSources, suggest) {
    if (evidenceSources && evidenceSources.length) {
        const evidence = evidenceSources.find(function(es) { return es && Number(es.ruleId) === Number(ruleId); });
        if (evidence) {
            const name = (evidence.displayName || "").trim();
            if (name && name !== "未命名事实") return name;
        }
    }
    if (suggest) {
        const rule = findSuggestRule(suggest, ruleId);
        if (rule) {
            const name = (rule.displayName || "").trim();
            if (name && name !== "未命名事实") return name;
            const section = (rule.sectionTitle || "").trim();
            if (section && section !== "未命名事实") return section;
            const subject = (rule.replySubject || "").trim();
            if (subject && subject !== "未命名事实") return subject;
        }
    }
    return "事实名称缺失";
}

function setAiReplyLoading(panel, loading, message = "AI 正在生成回复…", { stoppable = false, generationId = null, attemptTimeoutSeconds = 30, totalTimeoutSeconds = 300 } = {}) {
    if (!panel) return;
    panel.setAttribute("aria-busy", loading ? "true" : "false");
    panel.querySelectorAll("button, textarea, select, input").forEach((el) => {
        if (loading) {
            if (!el.hasAttribute("data-ai-reply-was-disabled")) {
                el.setAttribute("data-ai-reply-was-disabled", el.disabled ? "true" : "false");
            }
            el.disabled = true;
        } else if (el.hasAttribute("data-ai-reply-was-disabled")) {
            el.disabled = el.getAttribute("data-ai-reply-was-disabled") === "true";
            el.removeAttribute("data-ai-reply-was-disabled");
        }
    });
    let overlay = panel.querySelector(":scope > .ai-reply-loading-overlay");
    if (loading) {
        if (!overlay) {
            overlay = document.createElement("div");
            overlay.className = "ai-reply-loading-overlay";
            overlay.setAttribute("role", "status");
            overlay.setAttribute("aria-live", "polite");
            overlay.innerHTML =
                `<span class="ai-reply-loading-spinner" aria-hidden="true"></span>` +
                `<span class="ai-reply-loading-text"></span>`;
            if (stoppable) {
                overlay.insertAdjacentHTML("beforeend", `
                    <div class="ai-reply-progress" role="group" aria-label="AI 生成进度">
                        <div class="ai-reply-progress-phase">正在准备生成上下文</div>
                        <progress class="ai-reply-progress-track" aria-label="总 TTL 使用进度" max="100" value="0" aria-valuenow="0" title="已使用总 TTL 0/${totalTimeoutSeconds} 秒"></progress>
                        <div class="ai-reply-progress-detail">尚未调用模型 · 总计 0/${totalTimeoutSeconds} 秒</div>
                        <div class="ai-reply-progress-activity">等待服务端活动…</div>
                    </div>
                    <button type="button" class="button secondary small ai-reply-stop-button" data-action="ai-reply-stop">停止生成</button>`);
            }
            panel.appendChild(overlay);
        }
        const textEl = overlay.querySelector(".ai-reply-loading-text");
        if (textEl) textEl.textContent = message;
        const stop = overlay.querySelector("[data-action='ai-reply-stop']");
        if (stop) stop.disabled = false;
        if (stoppable && aiReplyState.latestProgress) renderAiReplyProgress(aiReplyState.latestProgress);
    } else if (overlay) {
        overlay.remove();
    }
}

function formatUnsupportedRequests(unsupportedRequests) {
    const items = (unsupportedRequests || []).filter((item) => String(item || "").trim());
    if (items.length === 0) return "";
    const shown = items.slice(0, 3).map((item) => String(item).trim());
    const rest = items.length - shown.length;
    let text = `以下请求缺少已审核依据：${shown.join("；")}`;
    if (rest > 0) {
        text += `；另 ${rest} 项`;
    }
    return text;
}

function collapseAiReplyRequestText(text) {
    return String(text || "").replace(/\s+/g, " ").trim().slice(0, 240);
}

function summarizeAiReplyCoverage(requestCoverage) {
    const items = Array.isArray(requestCoverage) ? requestCoverage : [];
    let grounded = 0;
    let partial = 0;
    let unsupported = 0;
    const reviewItems = [];
    let unknownCount = 0;
    items.forEach((item) => {
        const status = String(item && item.status != null ? item.status : "");
        const index = Number(item && item.index) || 0;
        const requestText = collapseAiReplyRequestText(item && item.requestText);
        if (status === "GROUNDED") {
            grounded += 1;
        } else if (status === "PARTIAL") {
            partial += 1;
            reviewItems.push({ index, requestText, status });
        } else if (status === "UNSUPPORTED") {
            unsupported += 1;
            reviewItems.push({ index, requestText, status });
        } else if (status) {
            unknownCount += 1;
        }
    });
    return {
        grounded,
        partial,
        unsupported,
        reviewItems,
        unknownCount,
        hasCoverage: items.length > 0,
        needsGroundingReview: partial > 0 || unsupported > 0
    };
}

function resolveAiDraftReadiness(result, coverageSummary) {
    if (result && result.draftReadiness === "READY") return "READY";
    if (result && result.draftReadiness === "NEEDS_REVIEW") return "NEEDS_REVIEW";
    if (result && result.draftReadiness === "BLOCKED") return "BLOCKED";
    if (coverageSummary && coverageSummary.hasCoverage) {
        if (coverageSummary.unsupported > 0) return "BLOCKED";
        if (coverageSummary.partial > 0) return "NEEDS_REVIEW";
        return "READY";
    }
    if (coverageSummary && (coverageSummary.reviewItems || []).length > 0) {
        return "NEEDS_REVIEW";
    }
    return "READY";
}

function formatAiReplyReviewWarnings(summary) {
    const items = (summary && summary.reviewItems) || [];
    return items.map((item) => {
        if (item.status === "PARTIAL") {
            return `第 ${item.index} 项仅部分有已审核依据：${item.requestText}；请人工补充后再发送。`;
        }
        return `第 ${item.index} 项缺少已审核依据：${item.requestText}；草稿未回答该项。`;
    });
}

function renderAiReplyFeedback(container, result, error = null) {
    if (!container) return;
    if (error) {
        container.hidden = false;
        container.innerHTML = `<div class="ai-reply-error">${escapeHtml(String(error))}</div>`;
        return;
    }
    if (!result) {
        container.hidden = true;
        container.innerHTML = "";
        return;
    }
    const requestCount = Number(result.requestCount) || 0;
    const groundedRequestCount = Number(result.groundedRequestCount) || 0;
    const warnings = Array.isArray(result.contextWarnings) ? result.contextWarnings : [];
    const unsupported = Array.isArray(result.unsupportedRequests) ? result.unsupportedRequests : [];
    const coverageSummary = Array.isArray(result.requestCoverage)
        ? summarizeAiReplyCoverage(result.requestCoverage)
        : null;
    const readiness = resolveAiDraftReadiness(result, coverageSummary);
    const failureCode = resolveAiReplyFailureReasonFromResult(result);
    const remainingWarnings = failureCode
        ? warnings.filter(function(w) { return !AI_REPLY_FAILURE_WARNING_CODES.has(w) && w !== failureCode; })
        : warnings;
    const parts = [];

    if (failureCode) {
        const failureText = failureCode === "FALLBACK_LLM_DISABLED"
            ? "LLM 功能未启用"
            : failureCode === "FALLBACK_CLIENT_UNAVAILABLE"
                ? "LLM 客户端不可用"
                : aiReplyFailureReasonLabel(failureCode);
        parts.push(
            `<div class="ai-reply-failure-banner" role="alert">` +
                `<strong>LLM 生成失败：${escapeHtml(failureText)}</strong>` +
                `<span>当前显示的是 QA 规则参考内容，未经过 LLM 自然化；不可直接采用或发送。</span>` +
            `</div>`
        );
        const heading = document.getElementById("trustDraftHeading");
        if (heading) heading.textContent = "QA 规则参考内容";
    }

    if (readiness === "READY") {
        parts.push(`<div class="ai-reply-coverage">草稿状态：依据完整</div>`);
    } else if (readiness === "NEEDS_REVIEW") {
        parts.push(`<div class="ai-reply-warning">草稿状态：部分问题需人工补充</div>`);
    } else if (readiness === "BLOCKED") {
        parts.push(`<div class="ai-reply-warning">草稿状态：存在缺少审核依据的问题，不可原样发送</div>`);
    }
    if (!failureCode) {
        if (result.generationState === "LLM_USED") {
            parts.push(
                `<div class="ai-reply-coverage">${escapeHtml(aiReplyGenerationStateLabel(result.generationState))}</div>`
            );
        } else if (result.generationState) {
            const stateLabel = aiReplyGenerationStateLabel(result.generationState);
            if (stateLabel) {
                parts.push(`<div class="ai-reply-warning">${escapeHtml(stateLabel)}</div>`);
            }
        }
    }
    if (coverageSummary && coverageSummary.hasCoverage) {
        parts.push(
            `<div class="ai-reply-coverage">${escapeHtml(
                `依据覆盖：完整 ${coverageSummary.grounded} 项 · 部分 ${coverageSummary.partial} 项 · 缺失 ${coverageSummary.unsupported} 项`
            )}</div>`
        );
        formatAiReplyReviewWarnings(coverageSummary).forEach((warning) => {
            parts.push(`<div class="ai-reply-warning">${escapeHtml(warning)}</div>`);
        });
        if (coverageSummary.unknownCount > 0) {
            parts.push(
                `<div class="ai-reply-warning">${escapeHtml("部分请求覆盖状态未知，请人工核对后再发送。")}</div>`
            );
        }
    } else {
        if (requestCount > 0) {
            parts.push(
                `<div class="ai-reply-coverage">事实覆盖 ${escapeHtml(String(groundedRequestCount))}/${escapeHtml(String(requestCount))} 项</div>`
            );
        }
        const unsupportedText = formatUnsupportedRequests(unsupported);
        if (unsupportedText) {
            parts.push(`<div class="ai-reply-warning">${escapeHtml(unsupportedText)}</div>`);
        }
    }
    remainingWarnings.forEach((code) => {
        const label = AI_REPLY_WARNING_LABELS[code] || String(code || "");
        if (!label) return;
        parts.push(`<div class="ai-reply-warning">${escapeHtml(label)}</div>`);
    });
    if (result.selectedModel) {
        parts.push(`<div class="ai-reply-coverage">模型：${escapeHtml(aiReplyModelLabel(result.selectedModel))}</div>`);
    }
    if (result.promptVersion) {
        parts.push(`<div class="ai-reply-coverage">Prompt 版本：${escapeHtml(result.promptVersion)}</div>`);
    }
    if (result.evidenceSetVersion) {
        const shortVersion = shortEvidenceHash(result.evidenceSetVersion);
        const evidenceCount = Array.isArray(result.evidenceSources) ? result.evidenceSources.length : 0;
        const availableCount = Array.isArray(result.evidenceSources) ? result.evidenceSources.filter(s => s && s.available).length : 0;
        parts.push(`<div class="ai-reply-coverage">证据集：${escapeHtml(shortVersion)} · ${availableCount}/${evidenceCount} 可用</div>`);
    }
    if (parts.length === 0) {
        container.hidden = true;
        container.innerHTML = "";
        return;
    }
    container.hidden = false;
    container.innerHTML = parts.join("");
}

function renderMailboxExpertTagEditor(expertRef, tags, editorId = "mailboxExpertTagEditor") {
    const orcidId = expertRef?.expertOrcidId || expertRef?.orcidId || "";
    if (!orcidId) return "";
    const level = expertRef?.expertIndexLevel || expertRef?.currentIndexLevel || "CANDIDATE";
    return renderExpertTagEditor(tags, orcidId, level, editorId);
}

const ES_MAX_RESULT_WINDOW = 10000;
const ES_PAGE_SIZE_MAX = 1000;

async function loadContacts() {
    const level = $("#expertIndexLevel").value;
    const size = Number($("#expertIndexSize").value || "50");
    const operatorStatus = $("#contactStatusFilter")?.value || "";
    const needsAttention = $("#contactNeedsAttentionFilter")?.value || "";
    const replyMode = $("#contactReplyModeFilter")?.value || "";
    const emailDomain = $("#expertEmailDomainFilter")?.value || "";
    const region = $("#expertRegionFilter")?.value || "";
    const discipline = $("#expertDisciplineFilter")?.value || "";
    let tag = $("#expertTagFilter")?.value || "";
    const useDbContactPath = needsAttention || replyMode;
    renderContactListSkeleton();

    const tagFilterEl = $("#expertTagFilter");
    const regionFilterEl = $("#expertRegionFilter");
    const disciplineFilterEl = $("#expertDisciplineFilter");
    const academicFilterIds = ["expertHIndexMinFilter", "expertCitationMinFilter", "expertRecentYearsFilter", "expertHasFieldFilter"];
    if (useDbContactPath) {
        tag = "";
        if (tagFilterEl) {
            tagFilterEl.value = "";
            tagFilterEl.disabled = true;
            tagFilterEl.parentElement.style.opacity = "0.5";
            tagFilterEl.parentElement.title = "标签筛选仅在 ES 查询模式下可用";
        }
        if (regionFilterEl) {
            regionFilterEl.value = "";
            regionFilterEl.disabled = true;
            regionFilterEl.parentElement.style.opacity = "0.5";
            regionFilterEl.parentElement.title = "地区筛选仅在 ES 查询模式下可用";
        }
        if (disciplineFilterEl) {
            disciplineFilterEl.value = "";
            disciplineFilterEl.disabled = true;
            disciplineFilterEl.parentElement.style.opacity = "0.5";
            disciplineFilterEl.parentElement.title = "学科筛选仅在 ES 查询模式下可用";
        }
        academicFilterIds.forEach((id) => {
            const el = $(`#${id}`);
            if (el) {
                if (el.tagName === "SELECT" && el.multiple && el.options) {
                    Array.from(el.options).forEach((opt) => { opt.selected = false; });
                } else {
                    el.value = "";
                }
                el.disabled = true;
                el.parentElement.style.opacity = "0.5";
                el.parentElement.title = "学术筛选仅在 ES 查询模式下可用";
            }
        });
    } else {
        if (tagFilterEl) {
            tagFilterEl.disabled = false;
            tagFilterEl.parentElement.style.opacity = "1";
            tagFilterEl.parentElement.title = "";
        }
        if (regionFilterEl) {
            regionFilterEl.disabled = false;
            regionFilterEl.parentElement.style.opacity = "1";
            regionFilterEl.parentElement.title = "";
        }
        if (disciplineFilterEl) {
            disciplineFilterEl.disabled = false;
            disciplineFilterEl.parentElement.style.opacity = "1";
            disciplineFilterEl.parentElement.title = "";
        }
        academicFilterIds.forEach((id) => {
            const el = $(`#${id}`);
            if (el) {
                el.disabled = false;
                el.parentElement.style.opacity = "1";
                el.parentElement.title = "";
            }
        });
    }

    const levelChanged = state.lastEmailProvidersLevel !== level;
    state.lastEmailProvidersLevel = level;

    const aggregationTag = useDbContactPath ? "" : tag;
    const aggregationRegion = useDbContactPath ? "" : region;
    const aggregationEmailDomain = useDbContactPath ? "" : emailDomain;

    loadEmailProviders(level, {
        filters: {
            tag: aggregationTag,
            operatorStatus,
            region: aggregationRegion
        }
    });
    loadRegions(level, {
        filters: {
            tag: aggregationTag,
            operatorStatus,
            emailDomain: aggregationEmailDomain
        }
    });
    loadExpertTagOptions(level, {
        filters: {
            operatorStatus,
            emailDomain: aggregationEmailDomain,
            region: aggregationRegion
        }
    });

    let contacts = [];
    let totalHits = 0;
    try {
    if (useDbContactPath) {
        const params = new URLSearchParams();
        if (operatorStatus) params.set("operatorStatus", operatorStatus);
        if (needsAttention) params.set("needsAttention", needsAttention);
        if (replyMode) params.set("replyMode", replyMode);
        const data = await api(`/api/expert-contacts?${params}`);
        let rawContacts = data.contacts || data;
        if (emailDomain) {
            rawContacts = rawContacts.filter(c => (c.expertEmail || "").endsWith(`@${emailDomain}`));
            totalHits = rawContacts.length;
        } else {
            totalHits = data.totalCount ?? rawContacts.length;
        }
        // MySQL 接口暂无分页，前端切片
        rawContacts = rawContacts.slice(state.contactsPage * size, (state.contactsPage + 1) * size);
        contacts = rawContacts.map(c => ({
            orcidId: c.orcidId,
            email: c.expertEmail,
            displayName: c.expertName,
            indexLevel: c.currentIndexLevel,
            indexLevelName: indexLevelLabels[c.currentIndexLevel] || c.currentIndexLevel,
            contactId: c.id,
            contactStatus: c.currentStatus,
            operatorStatus: c.operatorStatus,
            needsManualAttention: c.needsManualAttention,
            country: "",
            employment: "",
            keyword: "",
            tags: c.tags || [],
            updatedAt: c.updatedAt || null,
            hIndex: null,
            citationCount: null,
            lastPublicationYear: null,
            researchFields: "",
            institution: "",
            worksCount: null,
            enrichedAt: null
        }));
    } else {
        const params = new URLSearchParams();
        params.set("level", level);
        params.set("size", size);
        params.set("from", state.contactsPage * size);
        if (tag) params.set("tag", tag);
        if (operatorStatus) params.set("operatorStatus", operatorStatus);
        if (emailDomain) params.set("emailDomain", emailDomain);
        if (region) params.set("region", region);
        if (discipline) params.set("discipline", discipline);
        const sortBy = $("#expertSortBy")?.value || "";
        if (sortBy) params.set("sortBy", sortBy);
        const hIndexMin = $("#expertHIndexMinFilter")?.value || "";
        const citationMin = $("#expertCitationMinFilter")?.value || "";
        const recentYears = $("#expertRecentYearsFilter")?.value || "";
        const hasFieldEl = $("#expertHasFieldFilter");
        const hasField = hasFieldEl?.selectedOptions
            ? Array.from(hasFieldEl.selectedOptions).map((o) => o.value)
            : [];
        if (hIndexMin) params.set("hIndexMin", hIndexMin);
        if (citationMin) params.set("citationCountMin", citationMin);
        if (recentYears) params.set("recentYears", recentYears);
        hasField.forEach((f) => params.append("hasField", f));
        const data = await api(`/api/experts?${params}`);
        const rawExperts = data.experts || data;
        totalHits = data.totalHits ?? rawExperts.length;
        contacts = rawExperts.map(e => ({
            orcidId: e.orcidId,
            email: e.email,
            displayName: e.displayName,
            indexLevel: e.indexLevel,
            indexLevelName: e.indexLevelName,
            contactId: e.contactId,
            contactStatus: e.contactStatus,
            operatorStatus: e.operatorStatus,
            needsManualAttention: e.needsManualAttention,
            country: e.country,
            employment: e.employment,
            keyword: e.keyword,
            tags: e.tags || [],
            updatedAt: e.updatedAt || null,
            hIndex: e.hIndex ?? null,
            citationCount: e.citationCount ?? null,
            lastPublicationYear: e.lastPublicationYear ?? null,
            researchFields: e.researchFields || "",
            institution: e.institution || "",
            worksCount: e.worksCount ?? null,
            enrichedAt: e.enrichedAt || null
        }));
    }
    } catch (e) {
        state.contacts = [];
        $("#contactPager").hidden = true;
        $("#contactList").innerHTML = `
            <div class="list-empty">
                <span class="list-empty-title">加载失败</span>
                <span class="list-empty-hint">${escapeHtml(e.message || "请求出错")}，请点击「刷新」重试。</span>
            </div>`;
        $("#contactCountInfo").textContent = "";
        throw e;
    }

    const sortBy = $("#expertSortBy")?.value || "";
    if ((operatorStatus || needsAttention || replyMode) && sortBy === "updatedAt") {
        contacts.sort((a, b) => {
            if (!a.updatedAt) return 1;
            if (!b.updatedAt) return -1;
            return new Date(b.updatedAt) - new Date(a.updatedAt);
        });
    }
    state.contacts = contacts;
    state.contactsTotalHits = totalHits;

    $("#contactCountInfo").textContent =
        `筛选结果: ${totalHits} 位专家，当前显示 ${contacts.length} 位`;
    renderContactPager(size);
    loadOperatorStatusSyncTooltip();

    renderContactListItems();
    refreshAutoReplySummary().catch(() => {});
}

function renderContactListItems() {
    if (!state.contacts || state.contacts.length === 0) {
        $("#contactList").innerHTML = `
            <div class="list-empty">
                <span class="list-empty-title">没有符合条件的专家</span>
                <span class="list-empty-hint">试试切换漏斗层级或清除筛选条件。</span>
            </div>`;
        return;
    }

    $("#contactList").innerHTML = state.contacts.map((contact) => {
        const status = contact.operatorStatus
            ? operatorStatusLabels[contact.operatorStatus] || contact.operatorStatus
            : contact.contactId
                ? labelStatus(contact.contactStatus)
                : "未联系";
        const statusType = contactBadgeType(contact);
        const needsAttentionClass = contact.needsManualAttention ? "needs-attention" : "";
        const tagsHtml = (contact.tags || []).map(tag =>
            `<span class="expert-tag tag-${escapeHtml(tag)}">${escapeHtml(expertTagLabels[tag] || tag)}</span>`
        ).join("");
        const hIndexBadge = contact.hIndex != null
            ? `<span class="academic-badge academic-hindex" title="H-Index">h ${contact.hIndex}</span>`
            : "";
        const enrichedBadge = contact.enrichedAt
            ? `<span class="academic-badge academic-enriched" title="数据已补充 ${escapeHtml(contact.enrichedAt)}">已补充</span>`
            : "";
        const hoverInfo = [
            contact.orcidId ? `ORCID: ${contact.orcidId}` : "",
            contact.keyword || ""
        ].filter(Boolean).join("\n");
        return `
        <div class="list-item expert-list-item ${needsAttentionClass} ${state.selectedExpertOrcid === contact.orcidId ? "active" : ""}" data-action="select-expert" data-orcid="${escapeHtml(contact.orcidId)}" data-contact-id="${contact.contactId || ""}" ${hoverInfo ? `title="${escapeHtml(hoverInfo)}"` : ""}>
            <label class="expert-checkbox" onclick="event.stopPropagation()">
                <input type="checkbox" class="expert-select-cb" data-contact-id="${contact.contactId || ""}" ${!contact.contactId ? 'disabled' : ''}>
            </label>
            <div class="expert-content-wrapper">
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
                ${contact.employment || tagsHtml || hIndexBadge || enrichedBadge ? `
                <div class="expert-row-sub">
                    ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
                    ${hIndexBadge}${enrichedBadge}
                    ${tagsHtml ? `<span class="expert-row-tags">${tagsHtml}</span>` : ""}
                </div>` : ""}
            </div>
        </div>
        `;
    }).join("");
    // Stagger list-item entrance animation
    staggerListItems("#contactList .list-item");
}

function staggerListItems(selector, maxDelay) {
    const items = $$(selector);
    const cap = maxDelay || 600; // ms total budget
    const step = Math.min(40, cap / (items.length || 1));
    items.forEach((el, i) => {
        el.style.animationDelay = (i * step) + "ms";
    });
}

function contactBadgeType(contact) {
    const op = contact.operatorStatus;
    const cs = contact.contactStatus;
    if (op === "MANUAL_HANDOFF" || cs === "MANUAL_HANDOFF") return "warn";
    if (op === "COMPLETED") return "ok";
    if (op === "INVITED" || op === "MATERIALS_RECEIVED"
        || cs === "MEETING_INVITATION_SENT" || cs === "WAITING_MEETING_CONFIRMATION") return "primary";
    if (op === "REPLIED" || cs === "QA_AUTO_REPLIED") return "info";
    if (contact.contactId) return "ok";
    return "";
}

async function handleCheckReplies() {
    const taskType = "CHECK_REPLIES";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "检查回复", "checkRepliesBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}

async function executeCheckReplies() {
    const taskType = "CHECK_REPLIES";
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }

    const checked = $$(".expert-select-cb:checked")
        .map(cb => Number(cb.dataset.contactId))
        .filter(id => id > 0);

    const payload = checked.length > 0
        ? { contactIds: checked }
        : {};

    openTaskModal(taskType, "检查回复", "checkRepliesBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api("/api/mail/auto-reply/check-replies", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        if (response && response.executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
            return;
        }
        showStatus("检查回复失败: " + e.message, "error");
        showTaskErrorLog(e.message);
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
    }
}

// ---- 操作确认弹窗 ----
const filterReasonLabels = {
    MISSING_ORCID: "缺少 ORCID",
    INVALID_EMAIL_FORMAT: "邮箱格式无效",
    DISPOSABLE_EMAIL: "一次性邮箱",
    NO_DOCTORAL_DEGREE: "无博士学位",
    AGE_EXCEEDED: "超龄",
    CHINESE_NATIONALITY: "中国国籍",
    H_INDEX_TOO_LOW: "H-Index 过低",
    CITATION_COUNT_TOO_LOW: "引用数过低",
    INACTIVE: "近期无发表",
    "EMAIL:NO_MX_RECORD": "邮箱 MX 记录不存在",
    "EMAIL:INVALID_FORMAT": "邮箱格式无效",
    "EMAIL:DISPOSABLE_EMAIL": "一次性邮箱域名",
    "EMAIL:EMPTY_EMAIL": "邮箱为空",
    NO_ORCID_ID: "无 ORCID ID",
    ORCID_NOT_IN_OPENALEX: "OpenAlex 未收录此 ORCID",
    OPENALEX_API_ERROR: "OpenAlex API 错误",
    ES_UPDATE_FAILED: "ES 更新失败"
};

const filterItems = [
    { key: "candidate.requireOrcid",              label: "要求 ORCID",   type: "bool" },
    { key: "candidate.requireValidEmail",        label: "要求有效邮箱",  type: "bool" },
    { key: "candidate.requireDoctoralDegree",     label: "要求博士学位",  type: "bool" },
    { key: "candidate.excludeChineseNationality", label: "排除中国国籍",  type: "bool" },
    { key: "candidate.enableAgeFilter",           label: "年龄限制",     type: "bool" },
    { key: "candidate.maxAgeExclusive",           label: "最大年龄",     type: "number", dependsOn: "candidate.enableAgeFilter" },
    { key: "academic.enableHIndexFilter",         label: "H-Index 门槛", type: "bool" },
    { key: "academic.minHIndex",                  label: "最低 H-Index", type: "number", dependsOn: "academic.enableHIndexFilter" },
    { key: "academic.enableCitationFilter",       label: "引用数门槛",   type: "bool" },
    { key: "academic.minCitationCount",           label: "最低引用数",   type: "number", dependsOn: "academic.enableCitationFilter" },
    { key: "academic.enableActivityFilter",       label: "活跃度过滤",   type: "bool" },
    { key: "academic.recentYearsThreshold",       label: "近 N 年有发表", type: "number", dependsOn: "academic.enableActivityFilter" },
    { key: "email.enableMxCheck",                 label: "MX 邮箱验证",  type: "bool" }
];

function flattenFilters(filters) {
    const flat = {};
    if (!filters) return flat;
    Object.entries(filters.candidateFilter || {}).forEach(([k, v]) => {
        flat["candidate." + k] = v;
    });
    Object.entries(filters.academicFilter || {}).forEach(([k, v]) => {
        flat["academic." + k] = v;
    });
    Object.entries(filters.emailValidation || {}).forEach(([k, v]) => {
        flat["email." + k] = v;
    });
    return flat;
}

function renderFilterPanel(filters, preFlat) {
    var flat = preFlat || flattenFilters(filters);
    return filterItems.map(function(item) {
        const value = flat[item.key];
        if (item.type === "bool") {
            const checked = value === true || value === "true";
            return `<label class="filter-toggle">
                <input type="checkbox" data-filter-key="${item.key}" ${checked ? "checked" : ""}>
                <span>${escapeHtml(item.label)}</span>
            </label>`;
        } else {
            const parentEnabled = item.dependsOn ? (flat[item.dependsOn] === true || flat[item.dependsOn] === "true") : true;
            return `<label class="filter-number ${parentEnabled ? "" : "filter-disabled"}">
                <span>${escapeHtml(item.label)}:</span>
                <input type="number" data-filter-key="${item.key}" value="${value}" min="1" ${parentEnabled ? "" : "disabled"}>
            </label>`;
        }
    }).join("");
}

var filterUpdateDebounceTimer = null;
var filterSaveInFlightPromise = null;
var filterSaveInFlightPayload = null;
var lastFilterPayload = null;

function collectFilterPayload() {
    var payload = {};
    var panel = $("#taskLaunchFiltersPanel");
    if (!panel) return payload;
    panel.querySelectorAll("[data-filter-key]").forEach(function(input) {
        var key = input.dataset.filterKey;
        if (input.type === "checkbox") {
            payload[key] = input.checked ? "true" : "false";
        } else {
            payload[key] = String(input.value);
        }
    });
    return payload;
}

function payloadsEqual(a, b) {
    var aKeys = Object.keys(a).sort();
    var bKeys = Object.keys(b).sort();
    if (aKeys.length !== bKeys.length) return false;
    for (var i = 0; i < aKeys.length; i++) {
        if (aKeys[i] !== bKeys[i] || a[aKeys[i]] !== b[aKeys[i]]) return false;
    }
    return true;
}

function sendFilterSaveRequest(payload) {
    return api("/api/experts/eligibility-filters", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
}

function triggerFilterSave() {
    if (filterSaveInFlightPromise != null) return filterSaveInFlightPromise;
    if (lastFilterPayload == null) return Promise.resolve();
    var payload = {};
    var keys = Object.keys(lastFilterPayload);
    for (var i = 0; i < keys.length; i++) {
        payload[keys[i]] = lastFilterPayload[keys[i]];
    }
    filterSaveInFlightPayload = payload;
    filterSaveInFlightPromise = sendFilterSaveRequest(payload).then(function() {
        filterSaveInFlightPromise = null;
        if (lastFilterPayload != null && !payloadsEqual(lastFilterPayload, filterSaveInFlightPayload)) {
            filterSaveInFlightPayload = null;
            return triggerFilterSave();
        }
        lastFilterPayload = null;
        filterSaveInFlightPayload = null;
    }).catch(function(e) {
        filterSaveInFlightPromise = null;
        filterSaveInFlightPayload = null;
        throw e;
    });
    return filterSaveInFlightPromise;
}

function flushFilterSave() {
    if (filterUpdateDebounceTimer) {
        clearTimeout(filterUpdateDebounceTimer);
        filterUpdateDebounceTimer = null;
    }
    if (lastFilterPayload == null || Object.keys(lastFilterPayload).length === 0) return Promise.resolve();
    return triggerFilterSave();
}

function bindFilterToggleEvents() {
    var panel = $("#taskLaunchFiltersPanel");
    if (!panel) return;
    panel.querySelectorAll("[data-filter-key]").forEach(function(el) {
        el.addEventListener("change", function() {
            lastFilterPayload = collectFilterPayload();
            if (filterUpdateDebounceTimer) clearTimeout(filterUpdateDebounceTimer);
            filterUpdateDebounceTimer = setTimeout(function() {
                triggerFilterSave().catch(function(e) {
                    console.error("Failed to update filter settings:", e);
                });
            }, 300);
            renderFilterPanelFromUpdates();
        });
    });
}

function renderFilterPanelFromUpdates() {
    const panel = $("#taskLaunchFiltersPanel");
    if (!panel) return;
    const updateEls = panel.querySelectorAll("[data-filter-key]");
    if (updateEls.length === 0) return;
    const flat = {};
    updateEls.forEach(function(el) {
        flat[el.dataset.filterKey] = el.type === "checkbox" ? el.checked : el.value;
    });
    panel.innerHTML = renderFilterPanel({ candidateFilter: {}, academicFilter: {}, emailValidation: {} }, flat);
    bindFilterToggleEvents();
}

function renderFilterReasonsTable(filterReasons) {
    if (!filterReasons || Object.keys(filterReasons).length === 0) return "";
    var entries = [];
    for (var key in filterReasons) {
        if (Object.prototype.hasOwnProperty.call(filterReasons, key)) {
            entries.push({ key: key, count: filterReasons[key] });
        }
    }
    entries.sort(function(a, b) { return b.count - a.count; });
    return `<table class="filter-reasons-table">
        <caption>过滤原因分布</caption>
        <tbody>
            ${entries.map(function(e) {
                return `<tr><td>${escapeHtml(filterReasonLabels[e.key] || e.key)}</td><td>${e.count.toLocaleString()}</td></tr>`;
            }).join("")}
        </tbody>
    </table>`;
}

const taskLaunchConfigs = {
    EXPERT_REVALIDATION: {
        title: "重新验证候选人",
        desc: "将扫描所有 CANDIDATE 层专家，不符合条件的将被降级回 RAW。",
        btnId: "discoverBtn",
        showKeyword: false,
        showMaxPromotions: false,
        showFilters: true,
        preload: async () => {
            var filters = await api("/api/experts/eligibility-filters");
            return { desc: "将扫描所有 CANDIDATE 层专家，不符合条件的将被降级回 RAW。", canRun: true, filters: filters };
        },
        run: executeRevalidate
    },
    RAW_PROMOTION_SCAN: {
        title: "快速晋升（扫描 RAW）",
        desc: "将扫描 RAW 层专家，符合筛选条件的将被晋升到 CANDIDATE 层。",
        btnId: "discoverBtn",
        showKeyword: false,
        showMaxPromotions: false,
        showFilters: true,
        preload: async () => {
            var filters = await api("/api/experts/eligibility-filters");
            return { desc: "将扫描 RAW 层专家，符合以下筛选条件的将被晋升到 CANDIDATE 层。", canRun: true, filters: filters };
        },
        run: executePromoteRaw
    },
    EXPERT_DISCOVERY: {
        title: "深度发现（外部数据源）",
        desc: "从外部数据源搜索并导入新专家到系统中。",
        btnId: "discoverBtn",
        showKeyword: true,
        showMaxPromotions: false,
        run: executeDiscover
    },
    EXPERT_ENRICHMENT: {
        title: "补充学术数据（OpenAlex）",
        desc: "正在加载统计信息...",
        btnId: "discoverBtn",
        showKeyword: false,
        showMaxPromotions: false,
        preload: async () => {
            const stats = await api("/api/expert-discovery/enrich/stats");
            const desc = `CANDIDATE 层共 ${stats.total} 人，其中 ${stats.pending} 人待补充学术数据` +
                (stats.enrichedLast30d > 0 ? `（${stats.enrichedLast30d} 人已在 30 天内补充）` : '') + '。';
            return { desc, canRun: stats.pending > 0 };
        },
        run: executeEnrichExperts
    },
    MANUAL_INITIAL_OUTREACH: {
        title: "批量发送邮件",
        desc: "",
        btnId: "bulkOutreachBtn",
        showKeyword: false,
        showMaxPromotions: false,
        preload: async () => {
            const defaultType = ($("#expertTagFilter")?.value === "承诺回复材料")
                ? "MATERIAL_REMINDER" : "INTRODUCTION";
            batchSendType = defaultType;
            batchSendRequestToken = 0;
            const [config, status, composeTemplates, providers, pendingCount] = await Promise.allSettled([
                api(`/api/mail/batch-send/types/${defaultType}/config`),
                api(`/api/mail/batch-send/types/${defaultType}/status`),
                api("/api/compose-templates"),
                loadBatchSendTypeProviders(defaultType),
                api(`/api/mail/batch-send/types/${defaultType}/pending-count`)
            ]);
            return {
                desc: "",
                canRun: true,
                batchConfig: config.status === "fulfilled" ? config.value : null,
                batchStatus: status.status === "fulfilled" ? status.value : null,
                composeTemplates: composeTemplates.status === "fulfilled" ? composeTemplates.value : [],
                providers: providers.status === "fulfilled" ? providers.value : [],
                pendingCount: pendingCount.status === "fulfilled" ? pendingCount.value : null,
                defaultType
            };
        },
        run: executeManualOutreach
    },
    CHECK_REPLIES: {
        title: "检查回复",
        desc: "检查所有已联系专家的邮箱回复。",
        btnId: "checkRepliesBtn",
        showKeyword: false,
        showMaxPromotions: false,
        preload: async () => {
            const checked = $$(".expert-select-cb:checked")
                .map(cb => Number(cb.dataset.contactId))
                .filter(id => id > 0);
            if (checked.length > 0) {
                return { desc: `将检查已勾选的 ${checked.length} 位专家的邮件回复。`, canRun: true };
            }
            return { desc: "将检查全部已联系候选人的邮件回复。", canRun: true };
        },
        run: executeCheckReplies
    }
};

async function openTaskLaunchModal(taskType) {
    const config = taskLaunchConfigs[taskType];
    if (!config) return;

    if (taskType === "MANUAL_INITIAL_OUTREACH") {
        openBatchSendTaskModal();
        return;
    }

    const modal = $("#taskProgressModal");
    $("#taskModalTitle").textContent = config.title;

    // Show config section, hide progress section
    $("#taskModalConfigSection").hidden = false;
    $("#taskModalProgressSection").hidden = true;

    $("#taskLaunchDesc").textContent = config.desc || "";

    const runBtn = $("#taskLaunchRunBtn");
    runBtn.disabled = false;

    const filtersRow = $("#taskLaunchFiltersRow");
    filtersRow.hidden = true;

    let pre = null;
    if (config.preload) {
        $("#taskLaunchDesc").textContent = "正在准备任务信息...";
        runBtn.disabled = true;
        try {
            pre = await config.preload();
            $("#taskLaunchDesc").textContent = pre.desc;
            runBtn.disabled = !pre.canRun;
            if (pre.filters && config.showFilters) {
                $("#taskLaunchFiltersPanel").innerHTML = renderFilterPanel(pre.filters);
                filtersRow.hidden = false;
                bindFilterToggleEvents();
            }
        } catch (e) {
            $("#taskLaunchDesc").textContent = "加载任务信息失败: " + e.message;
            return;
        }
    }

    $("#taskLaunchKeywordRow").hidden = !config.showKeyword;
    $("#taskLaunchMaxPromotionsRow").hidden = !config.showMaxPromotions;
    if (config.showKeyword) $("#taskLaunchKeywordInput").value = "";
    if (config.showMaxPromotions) $("#taskLaunchMaxPromotions").value = "1000";

    const sourcesRow = $("#taskLaunchSourcesRow");
    if (taskType === "EXPERT_DISCOVERY") {
        sourcesRow.hidden = false;
        fetchSources().catch(() => {});
    } else {
        sourcesRow.hidden = true;
    }
    const advancedRow = $("#taskLaunchAdvancedRow");
    if (taskType === "EXPERT_DISCOVERY") {
        advancedRow.hidden = false;
        $("#taskLaunchIncludeRawScan").checked = false;
    } else {
        advancedRow.hidden = true;
    }
    $("#taskModalBySource").hidden = true;

    runBtn.onclick = async () => {
        if (config.showFilters) {
            runBtn.disabled = true;
            try {
                await flushFilterSave();
            } catch (e) {
                showStatus("筛选条件保存失败: " + e.message, "error");
                runBtn.disabled = false;
                return;
            }
            runBtn.disabled = false;
        }
        // Toggle view to progress immediately, then run the task
        $("#taskModalConfigSection").hidden = true;
        $("#taskModalProgressSection").hidden = false;
        config.run();
    };

    $("#taskModalRunBody").innerHTML = `<tr><td colspan="8" class="muted" style="text-align:center;padding:12px;">正在加载最近执行记录...</td></tr>`;
    $("#taskModalErrors").hidden = true;
    $("#taskModalErrorContent").textContent = "";

    modal.hidden = false;
    document.body.classList.add("modal-open");

    // Initialize currentTaskModal structure to fetch logs
    currentTaskModal = createTaskModalContext(taskType, config.title, config.btnId, "CONFIG");
    const capturedGeneration = currentTaskModal.generation;

    // Immediately load execution list
    fetchRunList(taskType, capturedGeneration);
}

function closeTaskLaunchModal() {
    closeTaskModal();
}

async function loadOperatorStatusSyncTooltip() {
    const btn = $("#backfillOperatorStatusBtn");
    if (!btn) return;
    try {
        const tasks = await api("/api/task-executions?taskType=CANDIDATE_OPERATOR_STATUS_SYNC");
        if (!tasks || tasks.length === 0) {
            btn.title = "暂无同步记录";
            return;
        }
        const task = tasks[0];
        const startedAt = task.startedAt || "-";
        const status = task.status || "-";
        const success = Number(task.successCount || 0);
        const failure = Number(task.failureCount || 0);
        let skipped = 0;
        if (task.resultSummary) {
            try {
                const summary = typeof task.resultSummary === "string"
                    ? JSON.parse(task.resultSummary)
                    : task.resultSummary;
                skipped = Number(summary.skipped || 0);
            } catch (_) {
                skipped = 0;
            }
        }
        let title = `最近同步: ${startedAt}\n状态: ${status}\n成功: ${success}, 失败: ${failure}, 跳过: ${skipped}`;
        if (task.errorMessage) {
            title += `\n错误: ${task.errorMessage}`;
        }
        btn.title = title;
    } catch (_) {
        btn.title = "暂无同步记录";
    }
}

async function handleBackfillOperatorStatus() {
    const btn = $("#backfillOperatorStatusBtn");
    if (btn) btn.disabled = true;
    showStatus("正在回刷 ES operatorStatus...", "info");
    try {
        const response = await api("/api/experts/backfill-operator-status", { method: "POST" });
        const total = Number(response.total || 0);
        const success = Number(response.success || 0);
        const failure = Number(response.failure || 0);
        const skipped = Number(response.skipped || 0);

        let msg = `回刷处理完成：总数 ${total}, 成功 ${success}, 失败 ${failure}, 跳过 ${skipped}`;
        if (skipped > 0) {
            msg += ` (CANDIDATE 文档不存在)`;
        }

        if (failure === 0) {
            showStatus(msg, "ok");
        } else if (success > 0) {
            showStatus(msg, "warn");
        } else {
            showStatus(msg, "error");
        }
    } catch (e) {
        showStatus("回刷失败: " + e.message, "error");
    } finally {
        if (btn) btn.disabled = false;
        loadOperatorStatusSyncTooltip();
    }
}

async function handleRevalidateCandidates() {
    const taskType = "EXPERT_REVALIDATION";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "重新验证候选人", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}

async function executeRevalidate() {
    const taskType = "EXPERT_REVALIDATION";
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "重新验证候选人", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api("/api/experts/revalidate-candidates", { method: "POST" });
        if (response && response.executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
        const result = response.result || response;
        const stats = result.stats || result;
        const failureMsg = stats.demotionFailed > 0 ? `, 降级失败 ${stats.demotionFailed}` : "";
        notifyTaskCompletionOnce({
            taskType,
            executionId: response.executionId,
            status: "COMPLETED",
            message: `候选人重新验证完成: 总数 ${stats.total}, 通过 ${stats.passed}, 降级 ${stats.demoted}${failureMsg}`,
            level: "ok"
        });
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
            return;
        }
        showStatus("验证失败: " + e.message, "error");
        showTaskErrorLog(e.message);
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
    }
}

async function handlePromoteRaw() {
    const taskType = "RAW_PROMOTION_SCAN";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "快速晋升（扫描 RAW）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}

async function handleDiscover() {
    const taskType = "EXPERT_DISCOVERY";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "深度发现（外部数据源）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}

async function handleDiscoverClick() {
    const runningRevalidate = await isTaskRunning("EXPERT_REVALIDATION");
    if (runningRevalidate) {
        openTaskModal("EXPERT_REVALIDATION", "重新验证候选人", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    const runningQuick = await isTaskRunning("RAW_PROMOTION_SCAN");
    if (runningQuick) {
        openTaskModal("RAW_PROMOTION_SCAN", "快速晋升（扫描 RAW）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    await handleDiscover();
}

async function handleDiscoverOption(mode) {
    if (mode === 'quick') {
        await handlePromoteRaw();
    } else if (mode === 'revalidate') {
        await handleRevalidateCandidates();
    } else if (mode === 'enrich') {
        await handleEnrichExperts();
    } else {
        await handleDiscover();
    }
}

async function handleEnrichExperts() {
    const taskType = "EXPERT_ENRICHMENT";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "补充学术数据（OpenAlex）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}

async function executeEnrichExperts() {
    const taskType = "EXPERT_ENRICHMENT";
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "补充学术数据（OpenAlex）", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api("/api/expert-discovery/enrich", { method: "POST" });
        const executionId = response?.executionId ?? response?.id;
        if (executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
            return;
        }
        showStatus(`补充学术数据失败: ${e.message}`, "error");
        showTaskErrorLog(e.message);
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
    }
}

async function executePromoteRaw() {
    const taskType = "RAW_PROMOTION_SCAN";
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "快速晋升（扫描 RAW）", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api("/api/experts/promote-eligible-raw", { method: "POST" });
        if (response && response.executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
        const result = response.result || response;
        const stats = result.stats || result;
        const failures = [];
        if (stats.promotionFailed > 0) failures.push(`晋升失败 ${stats.promotionFailed}`);
        if (stats.existenceCheckFailed > 0) failures.push(`HEAD 失败 ${stats.existenceCheckFailed}`);
        const failureMsg = failures.length > 0 ? `, ${failures.join(", ")}` : "";
        const hasFailures = stats.promotionFailed > 0 || stats.existenceCheckFailed > 0;
        const totalHits = stats.totalHits || stats.total;
        const coverageMsg = totalHits > stats.total ? `已处理 ${stats.total}/${totalHits}` : `总数 ${stats.total}`;
        notifyTaskCompletionOnce({
            taskType,
            executionId: response.executionId,
            status: "COMPLETED",
            message: `快速晋升（扫描 RAW）完成: ${coverageMsg}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}, 邮箱拒收 ${stats.emailRejected}${failureMsg}`,
            level: hasFailures ? "warn" : "ok"
        });
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
            return;
        }
        showStatus("扫描失败: " + e.message, "error");
        showTaskErrorLog(e.message);
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
    }
}

async function fetchSources() {
    try {
        const sources = await api(`/api/expert-discovery/sources`);
        const container = $("#taskLaunchSources");
        container.innerHTML = sources.map(s => `
            <label style="display:flex;align-items:center;gap:4px;padding:2px 8px;border:1px solid var(--panel-border);border-radius:4px;font-size:12px;cursor:pointer;">
                <input type="checkbox" value="${escapeHtml(s.sourceName)}"
                    ${s.enabled ? "checked" : "disabled"}
                    class="source-cb">
                ${escapeHtml(s.sourceName)}
                <span class="text-muted" style="font-size:10px;">(${escapeHtml(s.extractionMethod)})</span>
            </label>
        `).join("");
    } catch (e) {
        console.error("Failed to fetch sources:", e);
    }
}

function getSelectedSources() {
    const cbs = $$(".source-cb:checked:not([disabled])");
    if (cbs.length === 0) return [];
    return cbs.map(cb => cb.value);
}

async function executeDiscover() {
    const taskType = "EXPERT_DISCOVERY";
    const keywords = ($("#taskLaunchKeywordInput")?.value || "").trim();
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "深度发现（外部数据源）", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const selectedSources = getSelectedSources();
        const includeRawScan = $("#taskLaunchIncludeRawScan")?.checked === true;
        let response;
        if (keywords) {
            const params = new URLSearchParams();
            keywords.split(",").map(k => k.trim()).filter(k => k).forEach(k => params.append("keywords", k));
            if (selectedSources.length > 0) selectedSources.forEach(s => params.append("sources", s));
            if (includeRawScan) params.append("includeRawScan", "true");
            response = await api(`/api/expert-discovery/run/by-keyword?${params}`, { method: "POST" });
        } else {
            const params = new URLSearchParams();
            if (includeRawScan) params.append("includeRawScan", "true");
            const query = params.toString();
            const url = query ? `/api/expert-discovery/run?${query}` : "/api/expert-discovery/run";
            const body = selectedSources.length > 0 ? { sources: selectedSources } : {};
            response = await api(url, { method: "POST", body: JSON.stringify(body), headers: { "Content-Type": "application/json" } });
        }
        if (response && response.executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
        const result = response.result || response;
        const summary = result.resultSummary ? JSON.parse(result.resultSummary) : result;
        const stats = summary.stats || summary || {};
        const failures = [];
        if (stats.emailRejected > 0) failures.push(`邮箱拒绝 ${stats.emailRejected}`);
        if (stats.duplicates > 0) failures.push(`重复 ${stats.duplicates}`);
        if (stats.filtered > 0) failures.push(`过滤 ${stats.filtered}`);
        if (stats.rawWriteFailed > 0) failures.push(`RAW写入失败 ${stats.rawWriteFailed}`);
        if (stats.promotionFailed > 0) failures.push(`晋升失败 ${stats.promotionFailed}`);
        if (stats.dedupErrors > 0) failures.push(`查重错误 ${stats.dedupErrors}`);
        const failureMsg = failures.length > 0 ? `, ${failures.join(", ")}` : "";
        const hasFailures = failures.length > 0;
        notifyTaskCompletionOnce({
            taskType,
            executionId: response.executionId,
            status: "COMPLETED",
            message: `专家发现完成: 论文 ${stats.totalPapers || 0}, 作者 ${stats.totalAuthors || 0}, 收录 ${stats.indexed || 0}, 晋升 ${stats.promoted || 0}${failureMsg}`,
            level: hasFailures ? "warn" : "ok"
        });
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
            return;
        }
        showStatus("发现失败: " + e.message, "error");
        showTaskErrorLog(e.message);
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
    }
}

async function handleBulkOutreach() {
    const modal = document.getElementById("batchSendTaskModal");
    if (!modal) return;
    openBatchSendTaskModal();
}

const BATCH_SEND_TASK_TYPE = "MANUAL_INITIAL_OUTREACH";

// Open PROGRESS modal and POST a batch-send launch endpoint (shared by 开始/手动).
async function launchBatchSendWithProgress(endpoint, options = {}) {
    const taskType = BATCH_SEND_TASK_TYPE;
    const { successMessage, onError } = options;

    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return false;
    }

    openTaskModal(taskType, "批量发送邮件", "bulkOutreachBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api(endpoint, { method: "POST" });
        if (response && response.executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
        if (successMessage) showStatus(successMessage, "ok");
        return true;
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
        } else if (e.message.includes("额度已用尽")) {
            showModalToast(e.message, "warn");
            showStatus(e.message, "warn");
            stopTaskModalPolling();
            stopTaskWatcher(taskType, true);
            hideProgressBar();
        } else if (onError) {
            onError(e);
        } else {
            showStatus("启动发送失败: " + e.message, "error");
            showTaskErrorLog(e.message);
        }
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
        return false;
    }
}

async function executeManualOutreach() {
    // Replaced by batch send task console; execution flows through the new modal.
}

// ----- Batch Send controls (phase 04: I-2 / I-5 / I-8 / I-9 / L4-1 / L4-2) -----
let batchSendStatusTimer = null;
let batchSendBannerTimer = null;
let batchSendComposeTemplates = [];
let batchSendType = "INTRODUCTION";       // current active send type in dialog
let batchSendRequestToken = 0;            // I-11: increments on type switch; stale responses are ignored
const BATCH_SEND_STATUS_POLL_MS = 10000;
const BATCH_SEND_BANNER_POLL_MS = 30000;

function batchSendModeLabel(mode) {
    if (mode === "AUTO") return "自动定时";
    if (mode === "MANUAL") return "手动";
    return "—";
}

function batchSendStatusLabel(status) {
    if (status === "RUNNING") return "运行中";
    if (status === "PAUSED") return "已暂停";
    if (status === "SCHEDULED") return "定时中";
    if (status === "IDLE") return "空闲";
    return status || "—";
}

function batchSendStatusBadgeType(status) {
    if (status === "RUNNING") return "primary";
    if (status === "SCHEDULED") return "primary";
    if (status === "PAUSED") return "warn";
    return "";
}

// L4-1: button enable/disable driven entirely by backend status.
// Returns the disabled-state map for a given status so it can be unit-tested.
function batchSendButtonStates(status) {
    switch (status) {
        case "IDLE":
            return { start: false, pause: true, manual: false };
        case "RUNNING":
            return { start: true, pause: false, manual: true };
        case "PAUSED":
            return { start: false, pause: true, manual: false };
        default:
            return { start: true, pause: true, manual: true };
    }
}

// Pure DOM application of controls + badges + account table (I-2/I-8/I-9/L4-1).
function applyBatchSendControls(statusView) {
    if (!statusView) return;
    const status = statusView.status || "IDLE";
    const mode = statusView.mode || "NONE";
    const scheduleActive = status === "IDLE" && statusView.autoEnabled === true;
    const displayStatus = scheduleActive ? "SCHEDULED" : status;
    const states = batchSendButtonStates(status);

    // If a shared execution is RUNNING/CANCELLING, force-display activeSendType and lock type dropdown.
    const activeSendType = statusView.activeSendType || null;
    const execLocked = activeSendType && (status === "RUNNING" || status === "CANCELLING");
    const typeSel = $("#batchSendType");
    if (typeSel) {
        typeSel.disabled = !!execLocked;
        if (execLocked && typeSel.value !== activeSendType) {
            typeSel.value = activeSendType;
            batchSendType = activeSendType;
        }
    }

    const startBtn = $("#batchSendStartBtn");
    const pauseBtn = $("#batchSendPauseBtn");
    const manualBtn = $("#batchSendManualBtn");
    // 单个切换按钮：RUNNING 显示"暂停"(点击暂停)，其余显示"开始执行/继续"(点击启动/恢复)。
    // dataset.action 决定点击行为，由 handleBatchSendToggle 读取。
    if (startBtn) {
        if (status === "RUNNING" || scheduleActive) {
            startBtn.textContent = "暂停";
            startBtn.className = "button warn";
            startBtn.disabled = false;
            startBtn.dataset.action = "pause";
        } else if (status === "PAUSED") {
            startBtn.textContent = "继续/恢复";
            startBtn.className = "button primary";
            startBtn.disabled = false;
            startBtn.dataset.action = "start";
        } else if (status === "IDLE") {
            startBtn.textContent = "开始执行";
            startBtn.className = "button primary";
            startBtn.disabled = false;
            startBtn.dataset.action = "start";
        } else {
            startBtn.textContent = "开始执行";
            startBtn.className = "button primary";
            startBtn.disabled = true;
            startBtn.dataset.action = "start";
        }
    }
    // 开始/暂停已并入上方切换按钮，原暂停按钮始终隐藏。
    if (pauseBtn) { pauseBtn.hidden = true; pauseBtn.disabled = states.pause; }
    // 手动执行按钮始终保留显示；IDLE/PAUSED 可手动跑一轮，RUNNING 禁用。
    if (manualBtn) {
        manualBtn.hidden = false;
        manualBtn.disabled = states.manual;
    }

    const modeBadge = $("#batchSendModeBadge");
    if (modeBadge) {
        modeBadge.textContent = batchSendModeLabel(mode);
        modeBadge.className = "badge " + (mode === "AUTO" ? "primary" : mode === "MANUAL" ? "warn" : "");
    }
    // Prepend send-type label to status badge (S-3)
    const typeDisplayLabel = batchSendType === "MATERIAL_REMINDER" ? "材料提醒 " : "介绍 ";
    const statusBadge = $("#batchSendStatusBadge");
    if (statusBadge) {
        statusBadge.textContent = typeDisplayLabel + batchSendStatusLabel(displayStatus);
        statusBadge.className = "badge " + batchSendStatusBadgeType(displayStatus);
    }

    renderBatchSendAccountTable(statusView);
}

async function refreshBatchSendControls() {
    try {
        const statusView = await api(`${batchSendTypeBase()}/status`);
        applyBatchSendControls(statusView);
        return statusView;
    } catch (e) {
        // Status endpoint failure: leave controls as-is (don't blind the operator).
        return null;
    }
}

function fillBatchSendConfigForm(config) {
    if (!config) return;
    const setVal = (id, v) => { const el = $("#" + id); if (el) el.value = v; };

    // Parse cron to frequency + time
    const cron = (config.cron || "").trim();
    const cronParts = cron.split(/\s+/);
    let freq = "daily", time = "09:00";
    if (cronParts.length >= 5) {
        const [sec, min, hour, dom, mon, dow] = cronParts;
        if (hour === "*" || hour === "*/1") {
            freq = "hourly"; time = "";
        } else if (dow && dow !== "?" && dow !== "*") {
            freq = "weekly"; time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0");
        } else {
            freq = "daily"; time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0");
        }
    }
    setVal("batchSendFrequency", freq);
    setVal("batchSendTime", time);
    syncBatchSendTimeFieldVisibility();

    setVal("batchSendDailyCap", config.dailyCap ?? "");
    setVal("batchSendRoundSize", config.roundSize ?? "");
    // ms -> seconds for the UI
    setVal("batchSendPerMailIntervalSec", config.perMailIntervalMs != null ? Math.round(config.perMailIntervalMs / 1000) : "");
    setVal("batchSendPerRoundIntervalSec", config.perRoundIntervalMs != null ? Math.round(config.perRoundIntervalMs / 1000) : "");
    setVal("batchSendSelfCheckTtlMin", config.selfCheckTtlMinutes ?? "");
    // emailDomain is set via fillBatchSendProviderSelect to preserve option integrity
    setVal("batchSendDiscipline", config.discipline ?? "");
}

function fillBatchSendTemplateSelector(templates, selectedId, sendType) {
    const select = $("#batchSendTemplateId");
    if (!select) return;
    const type = sendType || batchSendType;
    const list = Array.isArray(templates) ? templates : [];
    const matchesType = (t) => t.mailType === type;
    const enabledTyped = list.filter((t) => t.enabled && matchesType(t));
    const selected = selectedId ? list.find((t) => t.id === selectedId) : null;

    let effectiveSelectedId = selectedId;
    if (selected && !matchesType(selected)) {
        effectiveSelectedId = null;
    }

    let optionsHtml = enabledTyped
        .map((t) => `<option value="${t.id}">${escapeHtml(t.templateName)}</option>`)
        .join("");
    if (selected && matchesType(selected) && !selected.enabled) {
        optionsHtml = `<option value="${selected.id}">${escapeHtml(selected.templateName)} (已禁用)</option>${optionsHtml}`;
    }

    // INTRODUCTION keeps a default empty option; MATERIAL_REMINDER requires explicit selection
    const defaultOption = type === "INTRODUCTION"
        ? '<option value="">默认 (INTRODUCTION)</option>'
        : "";
    select.innerHTML = `${defaultOption}${optionsHtml}`;
    select.value = effectiveSelectedId ? String(effectiveSelectedId) : "";
    refreshBatchSendTemplatePreview(effectiveSelectedId || null);
}

async function refreshBatchSendTemplatePreview(templateId) {
    const preview = $("#batchSendTemplatePreview");
    if (!preview) return;
    if (!templateId) {
        preview.innerHTML = '<span class="muted">使用系统默认 INTRODUCTION 模板</span>';
        return;
    }
    try {
        const p = await api(`/api/compose-templates/${templateId}/preview`);
        preview.innerHTML = `<strong>${escapeHtml(p.subject || "")}</strong><div class="pre">${escapeHtml(p.body || "")}</div>`;
    } catch (e) {
        preview.innerHTML = '<span class="muted">预览加载失败</span>';
    }
}

function syncBatchSendTimeFieldVisibility() {
    const freq = $("#batchSendFrequency")?.value;
    const timeField = $("#batchSendTimeField");
    if (timeField) timeField.style.display = freq === "hourly" ? "none" : "";
}

// Reads the form and returns the PUT payload (seconds -> ms). Throws on invalid input.
function readBatchSendConfigForm() {
    const val = (id) => $("#" + id)?.value;
    const num = (id) => {
        const raw = val(id);
        const n = Number(raw);
        if (raw === "" || raw == null || Number.isNaN(n)) throw new Error("字段 " + id + " 需为数字");
        return n;
    };
    // Build cron from frequency + time
    const freq = val("batchSendFrequency") || "daily";
    const timeParts = (val("batchSendTime") || "09:00").split(":");
    const hour = parseInt(timeParts[0] || "0", 10);
    const min = parseInt(timeParts[1] || "0", 10);
    let cron;
    if (freq === "hourly") {
        cron = "0 0 * * * ?";
    } else if (freq === "weekly") {
        cron = `0 ${min} ${hour} ? * MON`;
    } else {
        cron = `0 ${min} ${hour} * * ?`;
    }
    const payload = {
        autoEnabled: true,
        cron,
        dailyCap: Math.round(num("batchSendDailyCap")),
        roundSize: Math.round(num("batchSendRoundSize")),
        perMailIntervalMs: Math.round(num("batchSendPerMailIntervalSec") * 1000),
        perRoundIntervalMs: Math.round(num("batchSendPerRoundIntervalSec") * 1000),
        selfCheckTtlMinutes: Math.round(num("batchSendSelfCheckTtlMin")),
        emailDomain: val("batchSendEmailDomain") || "",
        discipline: val("batchSendDiscipline") || "",
        templateId: (() => {
            const raw = val("batchSendTemplateId");
            if (!raw) {
                if (batchSendType === "MATERIAL_REMINDER") throw new Error("材料提醒必须选择模板");
                return null;
            }
            const n = Number(raw);
            if (!Number.isFinite(n) || n <= 0) {
                if (batchSendType === "MATERIAL_REMINDER") throw new Error("材料提醒必须选择模板");
                return null;
            }
            const tmpl = batchSendComposeTemplates.find((t) => t.id === n);
            if (!tmpl || tmpl.mailType !== batchSendType) {
                if (batchSendType === "MATERIAL_REMINDER") throw new Error("材料提醒模板类型不匹配");
                return null;
            }
            return n;
        })()
    };
    if (payload.roundSize < 1) throw new Error("每轮数量需 ≥ 1");
    if (payload.dailyCap < payload.roundSize) throw new Error("每批上限需 ≥ 每轮数量");
    if (payload.perMailIntervalMs < 0) throw new Error("每封间隔需 ≥ 0");
    if (payload.perRoundIntervalMs < 0) throw new Error("每轮间隔需 ≥ 0");
    if (payload.selfCheckTtlMinutes < 1) throw new Error("自检 TTL 需 ≥ 1");
    return payload;
}

function showModalToast(message, type = "ok") {
    const el = $("#taskModalToast");
    if (!el) return;
    el.textContent = message;
    el.className = "task-modal-toast " + type;
    el.hidden = false;
    clearTimeout(showModalToast.timer);
    showModalToast.timer = setTimeout(() => { el.hidden = true; }, 3000);
}

async function saveBatchSendConfig() {
    const btn = $("#batchSendSaveConfigBtn");
    if (btn) btn.disabled = true;
    let payload;
    try {
        payload = readBatchSendConfigForm();
    } catch (e) {
        showModalToast("配置校验失败: " + e.message, "error");
        if (btn) btn.disabled = false;
        return;
    }
    try {
        const saved = await api(`${batchSendTypeBase()}/config`, { method: "PUT", body: JSON.stringify(payload) });
        fillBatchSendConfigForm(saved);
        await refreshBatchSendPendingCountDisplay();
        showModalToast("配置已保存", "ok");
    } catch (e) {
        showModalToast("保存失败: " + e.message, "error");
    } finally {
        if (btn) btn.disabled = false;
    }
}

// I-8: render per-account stats + flow-level summary from /batch-send/status (or progress.details).
function batchSendLimitReasonLabel(limitReason) {
    if (limitReason === "WARMUP_LIMIT_REACHED") return "预热达上限";
    if (limitReason === "DAILY_LIMIT_REACHED") return "已达今日上限";
    if (limitReason === "PAUSED_FAULT") return "故障暂停";
    return "";
}

function renderBatchSendAccountTable(statusView) {
    const panel = $("#batchSendProgressPanel");
    const tbody = $("#batchSendAccountTable");
    const summary = $("#batchSendSummaryRow");
    const warmupSummary = $("#batchSendWarmupSummary");
    if (!panel || !tbody) return;
    const accounts = Array.isArray(statusView?.accounts) ? statusView.accounts : [];
    const showPanel = accounts.length > 0 || statusView?.status === "RUNNING" || statusView?.status === "PAUSED";
    panel.hidden = !showPanel;
    if (!showPanel) return;

    const warmupCount = statusView?.warmupAccountCount ?? 0;
    const totalCapacity = statusView?.todayTotalCapacity ?? 0;
    const remainingCapacity = statusView?.todayRemainingCapacity ?? 0;
    const sentTodayTotal = accounts.reduce((sum, a) => sum + (a.todaySent || 0), 0);

    if (warmupSummary) {
        const showWarmup = warmupCount > 0 || totalCapacity > 0;
        warmupSummary.hidden = !showWarmup;
        if (showWarmup) {
            const warmupHtml = `预热账号 <strong>${warmupCount}</strong> · 今日额度 <strong>${sentTodayTotal}/${totalCapacity}</strong> · 剩余 <strong>${remainingCapacity}</strong>`;
            if (warmupSummary.__lastHtml !== warmupHtml) {
                warmupSummary.__lastHtml = warmupHtml;
                warmupSummary.innerHTML = warmupHtml;
            }
        }
    }

    if (summary) {
        const cap = statusView?.dailyCap ?? 0;
        const daily = statusView?.dailySentTotal ?? 0;
        const sent = statusView?.sentTotal ?? 0;
        const failed = statusView?.failedTotal ?? 0;
        const round = statusView?.roundNumber ?? 0;
        const templateName = statusView?.templateName;
        const templatePart = templateName
            ? ` · 当前模板 <strong>${escapeHtml(templateName)}</strong>`
            : "";
        const typeLabel = batchSendType === "MATERIAL_REMINDER" ? "材料提醒邮件" : "介绍邮件";
        const summaryHtml = `[${typeLabel}] 轮次 <strong>${round}</strong> · 每日 <strong>${daily}/${cap}</strong> · 累计成功 <strong>${sent}</strong> · 失败 <strong>${failed}</strong>${templatePart}`;
        // 内容未变化时跳过重写，避免轮询导致汇总行闪烁。
        if (summary.__lastHtml !== summaryHtml) {
            summary.__lastHtml = summaryHtml;
            summary.innerHTML = summaryHtml;
        }
    }

    const tableHtml = accounts.length === 0
        ? `<tr><td colspan="7" class="muted" style="text-align:center;padding:10px;">暂无账号统计</td></tr>`
        : accounts.map((a) => {
            const limitLabel = batchSendLimitReasonLabel(a.limitReason);
            const statusCell = a.paused
                ? `<span class="badge warn" title="${escapeHtml(a.pauseReason || "自动暂停")}">自动暂停</span>`
                : limitLabel
                    ? `<span class="badge warn batch-send-limit-label">${escapeHtml(limitLabel)}</span>`
                    : `<span class="badge ok">正常</span>`;
            const warmupBadge = a.warmupActive
                ? `<span class="badge info batch-send-warmup-badge">预热中</span>`
                : "";
            const effectiveLimit = a.effectiveDailyLimit ?? a.dailyLimit;
            const intervalMs = a.currentIntervalMs ?? 0;
            const intervalLabel = intervalMs >= 1000 ? `${(intervalMs / 1000).toFixed(1)}s` : `${intervalMs}ms`;
            return `
            <tr>
                <td><strong>${escapeHtml(a.accountCode)}</strong>${warmupBadge}</td>
                <td>${a.todaySent}/${a.dailyLimit}</td>
                <td>${effectiveLimit}</td>
                <td>${a.success}</td>
                <td>${a.failed}</td>
                <td>${intervalLabel}</td>
                <td>${statusCell}</td>
            </tr>`;
        }).join("");
    // 内容未变化时跳过重写，避免每 1s 进度轮询导致账号表整体闪烁。
    if (tbody.__lastHtml === tableHtml) return;
    tbody.__lastHtml = tableHtml;
    tbody.innerHTML = tableHtml;
}

// 单个切换按钮入口：RUNNING -> 暂停；IDLE/PAUSED -> 开始/恢复。
// 行为依据按钮 dataset.action（由 applyBatchSendControls 设置），避免再次读取状态。
async function handleBatchSendToggle() {
    const startBtn = $("#batchSendStartBtn");
    const statusView = await refreshBatchSendControls();
    const latestStatus = statusView?.status;
    const latestScheduleActive = latestStatus === "IDLE" && statusView?.autoEnabled === true;
    const action = statusView
        ? (latestStatus === "RUNNING" || latestScheduleActive ? "pause" : "start")
        : (startBtn?.dataset?.action || "start");
    if (action === "pause") {
        if (!confirm("确定要暂停批量发送吗？暂停后定时任务将不再自动发送。")) return;
        if (startBtn) startBtn.disabled = true;
        await handleBatchSendPause(statusView);
    } else {
        const isResume = (startBtn?.textContent || "").includes("继续") || (startBtn?.textContent || "").includes("恢复");
        const msg = isResume ? "确定要恢复自动定时发送吗？" : "确定要开始执行批量发送吗？";
        if (!confirm(msg)) return;
        if (startBtn) startBtn.disabled = true;
        await handleBatchSendStart();
    }
}

// Enables the cron-driven batch-send schedule. This never starts a send immediately.
async function enableBatchSendSchedule(status) {
    let payload;
    try {
        payload = readBatchSendConfigForm();
    } catch (e) {
        showModalToast("配置校验失败: " + e.message, "error");
        return false;
    }
    const saved = await api(`${batchSendTypeBase()}/config`, { method: "PUT", body: JSON.stringify(payload) });
    fillBatchSendConfigForm(saved);
    if (status === "PAUSED") {
        await api(`${batchSendTypeBase()}/resume-schedule`, { method: "POST" });
    }
    showModalToast(status === "PAUSED" ? "已恢复定时发送" : "定时器已启动", "ok");
    showStatus(status === "PAUSED" ? "已恢复自动定时发送，将按配置时间执行" : "定时器已启动，将按配置时间执行", "ok");
    await refreshBatchSendControls();
    return true;
}

// "开始执行/继续" handler. IDLE/PAUSED -> enable cron schedule; no immediate send.
async function handleBatchSendStart() {
    const startBtn = $("#batchSendStartBtn");
    if (startBtn) startBtn.disabled = true;
    try {
        const statusView = await refreshBatchSendControls();
        const status = statusView?.status || "IDLE";
        await enableBatchSendSchedule(status);
    } catch (e) {
        showStatus("启动定时器失败: " + e.message, "error");
    } finally {
        refreshBatchSendControls().catch(() => {});
    }
}

async function handleBatchSendPause(statusViewOverride = null) {
    const pauseBtn = $("#batchSendPauseBtn");
    if (pauseBtn) pauseBtn.disabled = true;
    try {
        const statusView = statusViewOverride || await refreshBatchSendControls();
        const status = statusView?.status || "IDLE";
        const endpoint = status === "RUNNING"
            ? `${batchSendTypeBase()}/pause`
            : `${batchSendTypeBase()}/pause-schedule`;
        await api(endpoint, { method: "POST" });
        const message = status === "RUNNING" ? "已请求暂停批量发送" : "已暂停定时发送";
        showModalToast(message, "ok");
        showStatus(message, "ok");
    } catch (e) {
        showStatus("暂停失败: " + e.message, "error");
    } finally {
        refreshBatchSendControls().catch(() => {});
    }
}

async function handleBatchSendManual() {
    if (!confirm("确定要手动执行一轮发送吗？")) return;
    const manualBtn = $("#batchSendManualBtn");
    if (manualBtn) manualBtn.disabled = true;
    try {
        await launchBatchSendWithProgress(`${batchSendTypeBase()}/manual`, {
            successMessage: "已请求手动执行一轮发送",
            onError(e) {
                const msg = e.message || "";
                if (msg.includes("额度已用尽")) {
                    showModalToast(msg, "warn");
                    showStatus(msg, "warn");
                } else if (msg.includes("IDLE 或 PAUSED") || msg.includes("手动执行仅")) {
                    showStatus("当前流程正在运行，结束或暂停后再使用手动执行", "warn");
                } else {
                    showStatus("手动执行失败: " + msg, "error");
                }
            }
        });
    } finally {
        refreshBatchSendControls().catch(() => {});
    }
}

function startBatchSendStatusPoll() {
    stopBatchSendStatusPoll();
    batchSendStatusTimer = setInterval(() => {
        refreshBatchSendControls().catch(() => {});
    }, BATCH_SEND_STATUS_POLL_MS);
}

function stopBatchSendStatusPoll() {
    if (batchSendStatusTimer) {
        clearInterval(batchSendStatusTimer);
        batchSendStatusTimer = null;
    }
}

// Returns the API base path for the current send type.
function batchSendTypeBase(sendType) {
    return `/api/mail/batch-send/types/${sendType || batchSendType}`;
}

// Loads provider list for the given send type.
async function loadBatchSendTypeProviders(sendType) {
    const params = sendType === "MATERIAL_REMINDER"
        ? "level=APPLICATION&tag=%E6%89%BF%E8%AF%BA%E5%9B%9E%E5%A4%8D%E6%9D%90%E6%96%99"
        : "level=CANDIDATE&operatorStatus=NOT_CONTACTED";
    return api(`/api/experts/email-providers?${params}`);
}

// Fills #batchSendEmailDomain with providerList and restores savedValue.
// If savedValue is not in new options, inserts a "当前配置（无匹配）" fallback.
function fillBatchSendProviderSelect(sendType, providerList, savedValue) {
    const select = $("#batchSendEmailDomain");
    if (!select) return;
    const domains = Array.isArray(providerList) ? providerList : [];
    let html = '<option value="">全部</option>';
    domains.forEach(d => {
        const v = escapeHtml(d.domain || d);
        const label = d.count != null ? `${v} (${d.count})` : v;
        html += `<option value="${v}">${label}</option>`;
    });
    select.innerHTML = html;
    if (savedValue) {
        select.value = savedValue;
        if (select.value !== savedValue) {
            // Saved value not in new options — add fallback entry
            const fb = document.createElement("option");
            fb.value = savedValue;
            fb.textContent = "当前配置（无匹配）";
            select.insertBefore(fb, select.firstChild);
            select.value = savedValue;
        }
    }
}

// Fixed range copy per send type.
function batchSendRangeCopy(sendType) {
    if (sendType === "MATERIAL_REMINDER") {
        return '范围：APPLICATION 层"承诺回复材料"标签专家；发送成功后保留标签';
    }
    return "范围：CANDIDATE 未联系专家及失败待补发专家";
}

// Updates #batchSendRecipientSummary with fixed copy + pending count.
function applyBatchSendRecipientSummary(sendType, pendingCountRes) {
    const el = $("#batchSendRecipientSummary");
    if (!el) return;
    const rangeCopy = batchSendRangeCopy(sendType);
    if (pendingCountRes == null) {
        el.textContent = rangeCopy;
        return;
    }
    const count = Number(pendingCountRes.pending ?? pendingCountRes.count ?? pendingCountRes ?? 0);
    el.textContent = `${rangeCopy}。待发送：${count} 封`;
}

// Refreshes the pending count display for the current batchSendType (token-aware).
async function refreshBatchSendPendingCountDisplay() {
    const token = batchSendRequestToken;
    const sendType = batchSendType;
    try {
        const res = await api(`${batchSendTypeBase(sendType)}/pending-count`);
        if (token !== batchSendRequestToken || sendType !== batchSendType) return;
        applyBatchSendRecipientSummary(sendType, res);
    } catch (e) {
        if (token !== batchSendRequestToken || sendType !== batchSendType) return;
        const el = $("#batchSendRecipientSummary");
        if (el) el.textContent = `${batchSendRangeCopy(sendType)}。（待发送数量加载失败）`;
    }
}

// Disables save/start/manual buttons during type switch.
function setBatchSendControlsEnabled(enabled) {
    const ids = ["batchSendSaveConfigBtn", "batchSendStartBtn", "batchSendManualBtn"];
    ids.forEach(id => {
        const el = $("#" + id);
        if (el) el.disabled = !enabled;
    });
}

// Handler called when #batchSendType select changes (I-11).
async function onBatchSendTypeChange() {
    const typeSel = $("#batchSendType");
    const newType = typeSel?.value || "INTRODUCTION";
    batchSendType = newType;
    const token = ++batchSendRequestToken;

    setBatchSendControlsEnabled(false);

    // Update template label text
    const templateLabel = $("#batchSendTemplateLabel");
    if (templateLabel) {
        templateLabel.textContent = newType === "MATERIAL_REMINDER" ? "材料提醒邮件模板" : "介绍邮件模板";
    }

    let configResult = null, statusResult = null, providersResult = [], pendingResult = null;
    try {
        const [cfg, st, prov, pend] = await Promise.allSettled([
            api(`${batchSendTypeBase(newType)}/config`),
            api(`${batchSendTypeBase(newType)}/status`),
            loadBatchSendTypeProviders(newType),
            api(`${batchSendTypeBase(newType)}/pending-count`)
        ]);
        if (token !== batchSendRequestToken) return;
        configResult = cfg.status === "fulfilled" ? cfg.value : null;
        statusResult = st.status === "fulfilled" ? st.value : null;
        providersResult = prov.status === "fulfilled" ? prov.value : [];
        pendingResult = pend.status === "fulfilled" ? pend.value : null;
    } catch (e) {
        if (token !== batchSendRequestToken) return;
        showModalToast("切换发送类型失败: " + e.message, "error");
        setBatchSendControlsEnabled(true);
        return;
    }

    fillBatchSendProviderSelect(newType, providersResult, configResult?.emailDomain ?? null);
    if (configResult) fillBatchSendConfigForm(configResult);
    fillBatchSendTemplateSelector(batchSendComposeTemplates, configResult?.templateId ?? null);
    applyBatchSendRecipientSummary(newType, pendingResult);
    if (statusResult) applyBatchSendControls(statusResult);
    setBatchSendControlsEnabled(true);
}

// Returns banner text for a single type status, or null if no banner needed.
function batchSendBannerTextForType(statusView, typeLabel) {
    if (!statusView) return null;
    const pauseReason = statusView.pauseReason || "";
    const message = statusView.message || "";
    const isNoAccount = statusView.status === "PAUSED" && pauseReason === "NO_AVAILABLE_ACCOUNT";
    const isWarmupLimit = pauseReason === "WARMUP_LIMIT_REACHED" || message.includes("预热上限");
    const isDailyLimit = pauseReason === "DAILY_LIMIT_REACHED"
        || (message.includes("今日发送上限") && !isWarmupLimit);
    if (!isNoAccount && !isWarmupLimit && !isDailyLimit) return null;
    if (isNoAccount) return `${typeLabel}批量发送已暂停：无可用邮箱账号，请检查并恢复账号。`;
    if (isWarmupLimit) return `${typeLabel}已达到预热上限，今日暂停发送`;
    return `${typeLabel}已达到今日发送上限`;
}

// I-5 / L4-2: banner source of truth is GET /batch-send/types/{type}/status (both types queried).
function applyBatchSendBanner(introStatus, materialStatus) {
    const banner = $("#batchSendPausedBanner");
    if (!banner) return;
    const textEl = $("#batchSendPausedBannerText");
    // Support legacy single-arg call (introStatus only)
    const introText = batchSendBannerTextForType(introStatus, "介绍邮件");
    const materialText = materialStatus != null
        ? batchSendBannerTextForType(materialStatus, "材料提醒邮件")
        : null;
    const messages = [introText, materialText].filter(Boolean);
    const showBanner = messages.length > 0;
    banner.hidden = !showBanner;
    if (!showBanner || !textEl) return;
    textEl.textContent = messages.join("；");
}

async function refreshBatchSendBanner() {
    try {
        const [introRes, materialRes] = await Promise.allSettled([
            api("/api/mail/batch-send/types/INTRODUCTION/status"),
            api("/api/mail/batch-send/types/MATERIAL_REMINDER/status")
        ]);
        const introStatus = introRes.status === "fulfilled" ? introRes.value : null;
        const materialStatus = materialRes.status === "fulfilled" ? materialRes.value : null;
        applyBatchSendBanner(introStatus, materialStatus);
        return introStatus;
    } catch (e) {
        return null;
    }
}

function initBatchSendBanner() {
    refreshBatchSendBanner().catch(() => {});
    stopBatchSendBannerPoll();
    batchSendBannerTimer = setInterval(() => {
        refreshBatchSendBanner().catch(() => {});
    }, BATCH_SEND_BANNER_POLL_MS);
}

function stopBatchSendBannerPoll() {
    if (batchSendBannerTimer) {
        clearInterval(batchSendBannerTimer);
        batchSendBannerTimer = null;
    }
}

async function showPollLog() {
    let data;
    try {
        data = await api("/api/task-executions/recent-polls?limit=10");
    } catch (e) {
        showStatus("轮询日志加载失败: " + e.message, "error");
        return;
    }
    if (!Array.isArray(data)) {
        showStatus("轮询日志接口返回格式异常", "error");
        return;
    }
    const rows = data.map(log => {
        const triggerLabel = log.triggerType === "SCHEDULED" ? "定时"
            : log.triggerType === "MANUAL_ALL" ? "手动(全量)"
            : log.triggerType === "MANUAL_SELECTIVE" ? "手动(指定)" : log.triggerType;
        const triggerBadge = log.isManualTrigger
            ? badge("手动", "warn")
            : badge("定时", "");
        const statusBadge = badge(log.status === "SUCCESS" ? "成功"
            : log.status === "PARTIAL_SUCCESS" ? "部分成功"
            : log.status === "FAILED" ? "失败"
            : log.status === "RUNNING" ? "运行中" : log.status,
            log.status === "SUCCESS" ? "ok" : log.status === "FAILED" ? "error" : "warn");
        const replyCount = log.expertsWithReply?.length || 0;
        const progressText = log.status === "RUNNING"
            ? "执行中..."
            : [
                `${log.accountsPolled}/${log.totalAccountsToPoll} 账号`,
                log.totalFetched > 0 ? `拉取 ${log.totalFetched} 封` : null,
                log.totalReplied > 0 ? `回复 ${log.totalReplied}` : null,
                log.totalManualReview > 0 ? `转人工 ${log.totalManualReview}` : null
            ].filter(Boolean).join(" · ") || "无活动";

        return `
        <tr class="poll-log-row" data-poll-id="${log.id}" style="cursor:pointer;" title="点击查看详情">
            <td>${triggerBadge} ${escapeHtml(triggerLabel)}</td>
            <td>${escapeHtml(log.startedAt)}</td>
            <td>${progressText}</td>
            <td>${replyCount > 0
                ? `<span style="color:var(--primary);font-weight:600;">${replyCount}</span> 位`
                : "0"}</td>
            <td>${log.durationSeconds != null ? log.durationSeconds + " 秒" : "-"}</td>
            <td>${escapeHtml(log.nextPollAt || "-")}</td>
            <td>${statusBadge}</td>
        </tr>`;
    }).join("");

    const panel = $("#pollLogPanel");
    if (panel) {
        const tbody = $("#pollLogBody");
        if (tbody) {
            tbody.innerHTML = rows || '<tr><td colspan="7" class="text-muted" style="text-align:center;padding:12px;">暂无轮询记录</td></tr>';
        }
        panel.hidden = false;
        panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
}

async function togglePollDetail(row) {
    const id = row.dataset.pollId;
    const existingDetail = row.nextElementSibling;
    if (existingDetail?.classList.contains("poll-detail-row")) {
        existingDetail.remove();
        return;
    }

    const data = await api(`/api/task-executions/recent-polls/${id}/detail`);

    const detailRow = document.createElement("tr");
    detailRow.className = "poll-detail-row";
    detailRow.innerHTML = `<td colspan="7" style="padding: 12px 16px; background: var(--surface);">
        ${data.error ? `<div class="text-muted" style="margin-bottom:8px;">${escapeHtml(data.error)}</div>` : ""}
        ${data.accounts.map(acct => `
            <div style="margin-bottom: 14px; padding: 8px; border: 1px solid var(--border); border-radius: 4px;">
                <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">
                    <strong>${escapeHtml(acct.accountCode)}</strong>
                    ${badge(acct.status === "SUCCESS" ? "成功" : "失败",
                            acct.status === "SUCCESS" ? "ok" : "error")}
                    <span class="text-muted" style="font-size:12px;">
                        拉取 ${acct.fetched} · 记录 ${acct.recorded} · 回复 ${acct.replied} · 转人工 ${acct.manualReview}
                    </span>
                </div>
                ${acct.errorMessage ? `<div style="color:var(--error);font-size:12px;margin-bottom:4px;">${escapeHtml(acct.errorMessage)}</div>` : ""}
                ${acct.repliedExperts.length > 0 ? `
                    <table style="width:100%;font-size:12px;border-collapse:collapse;">
                        <thead><tr>
                            <th style="text-align:left;padding:4px 6px;border-bottom:1px solid var(--line);">专家邮箱</th>
                            <th style="text-align:left;padding:4px 6px;border-bottom:1px solid var(--line);">专家姓名</th>
                            <th style="text-align:left;padding:4px 6px;border-bottom:1px solid var(--line);">处理结果</th>
                        </tr></thead>
                        <tbody>${acct.repliedExperts.map(e => `
                            <tr>
                                <td style="padding:3px 6px;">${escapeHtml(e.expertEmail || "-")}</td>
                                <td style="padding:3px 6px;">${escapeHtml(e.expertName || "-")}</td>
                                <td style="padding:3px 6px;">${badge(outcomeLabel(e.outcome), outcomeType(e.outcome))}</td>
                            </tr>
                        `).join("")}</tbody>
                    </table>
                ` : '<div class="text-muted" style="font-size:12px;margin-top:4px;">该账号未收到新邮件</div>'}
            </div>
        `).join("")}
        ${data.accounts.length === 0 ? '<div class="text-muted">无账号轮询数据</div>' : ""}
    </td>`;
    row.after(detailRow);
}

function outcomeLabel(outcome) {
    const map = {
        QA_REPLIED: "QA 自动回复",
        MEETING_INVITED: "会议邀约",
        MANUAL_REVIEW_BY_INTENT: "转人工(意图)",
        QA_NO_MATCH: "转人工(QA未匹配)",
        CLOSED_BY_INTENT: "关闭(意图)",
        AUTO_REPLY_DISABLED: "跳过(自动回复关闭)",
        MANUAL_HANDOFF_STATUS: "跳过(已转人工)",
        UNMATCHED_CONTACT: "未匹配联系人",
        DUPLICATE_IMAP_UID: "重复邮件",
        MEETING_ALREADY_SENT: "会议已发送",
        INTRODUCTION_NOT_SENT: "未发首封"
    };
    return map[outcome] || outcome;
}

function outcomeType(outcome) {
    if (outcome === "QA_REPLIED" || outcome === "MEETING_INVITED") return "ok";
    if (outcome === "CLOSED_BY_INTENT" || outcome === "UNMATCHED_CONTACT") return "error";
    return "warn";
}

function formatDateTime(isoStr) {
    if (!isoStr) return "-";
    const str = isoStr.replace("T", " ");
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(str)) {
        return str + ":00";
    }
    return str.replace(/\.\d+$/, "").substring(0, 19);
}


function renderKeywords(keywordString) {
    if (!keywordString) return `<span class="text-muted">无关键词</span>`;
    const list = keywordString.split(/[,,，，;；]/).map(k => k.trim()).filter(Boolean);
    if (list.length === 0) return `<span class="text-muted">无关键词</span>`;
    return `<div class="keywords-container">
        ${list.map(k => `<span class="keyword-pill">${escapeHtml(k)}</span>`).join("")}
    </div>`;
}

function backToListBtnHtml() {
    return `
        <button class="button small back-to-list" onclick="scrollBackToContactsList()">
            <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
            返回列表
        </button>`;
}

function scrollBackToContactsList() {
    document.querySelector(".contacts-list-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function scrollBackToContactsList() {
    document.querySelector(".contacts-list-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderDetailSubTabs(activeTab = "academic") {
    const tabs = [
        { key: "academic", label: "学术档案" },
        { key: "contact", label: "联系详情" },
        { key: "template", label: "模板预览" }
    ];
    return `
    <div class="detail-sub-tabs">
        ${tabs.map((t) => `
            <button type="button" class="detail-sub-tab ${t.key === activeTab ? "active" : ""}" data-sub-tab="${t.key}">
                ${t.label}
            </button>
        `).join("")}
    </div>`;
}

function renderAcademicProfilePanel(expert) {
    return `
        <div class="metadata-grid academic-metrics-row">
            <div class="metadata-card">
                <div class="metadata-card-header"><span>H-INDEX</span></div>
                <div class="metadata-card-value academic-metric-value">${expert.hIndex ?? "-"}</div>
            </div>
            <div class="metadata-card">
                <div class="metadata-card-header"><span>引用数</span></div>
                <div class="metadata-card-value academic-metric-value">${expert.citationCount != null ? expert.citationCount.toLocaleString() : "-"}</div>
            </div>
            <div class="metadata-card">
                <div class="metadata-card-header"><span>发表数</span></div>
                <div class="metadata-card-value academic-metric-value">${expert.worksCount ?? "-"}</div>
            </div>
            <div class="metadata-card">
                <div class="metadata-card-header"><span>最近发表</span></div>
                <div class="metadata-card-value academic-metric-value">${expert.lastPublicationYear ?? "-"}</div>
            </div>
        </div>
        ${expert.researchFields ? `
        <div class="metadata-grid">
            <div class="metadata-card span-all">
                <div class="metadata-card-header"><span>研究方向</span></div>
                <div class="metadata-card-value">${escapeHtml(expert.researchFields)}</div>
            </div>
        </div>` : ""}
        ${expert.institution ? `
        <div class="metadata-grid">
            <div class="metadata-card span-all">
                <div class="metadata-card-header"><span>机构</span></div>
                <div class="metadata-card-value">${escapeHtml(expert.institution)}</div>
            </div>
        </div>` : ""}
        ${expert.enrichedAt ? `
        <div class="enrichment-status-info">
            <span>数据来源: OpenAlex</span>
            <span>更新时间: ${escapeHtml(expert.enrichedAt)}</span>
        </div>` : `
        <div class="enrichment-status-info enrichment-empty">
            <span>尚未补充学术数据</span>
        </div>`}
    `;
}

function activateDetailSubTab(btn) {
    const tabKey = btn.dataset.subTab;
    const detail = btn.closest(".detail");
    if (!detail || !tabKey) return;
    detail.querySelectorAll(".detail-sub-tab").forEach((t) => {
        t.classList.toggle("active", t.dataset.subTab === tabKey);
    });
    detail.querySelectorAll(".detail-tab-panel").forEach((p) => {
        p.hidden = p.dataset.panel !== tabKey;
    });
    if (tabKey === "template") {
        const panel = detail.querySelector('[data-panel="template"]');
        if (panel && !panel.dataset.loaded) {
            loadTemplatePreview(panel, state.selectedExpertOrcid);
        }
    }
}

async function loadTemplatePreview(panel, orcidId) {
    if (!orcidId) {
        panel.innerHTML = `<div class="tpl-var-empty">无 ORCID，无法预览模板变量。</div>`;
        return;
    }
    panel.innerHTML = `<div class="tpl-var-loading">加载模板变量中...</div>`;
    panel.dataset.loaded = "true";
    try {
        const level = $("#expertIndexLevel")?.value || "CANDIDATE";
        const vars = await api(`/api/experts/template-variables?orcidId=${encodeURIComponent(orcidId)}&level=${level}`);
        const filled = vars.filter((v) => v.filled).length;
        const total = vars.length;
        const coveragePercent = total > 0 ? Math.round(filled / total * 100) : 0;
        panel.innerHTML = `
            <div class="tpl-var-summary">
                <span class="tpl-var-coverage">变量覆盖: ${filled}/${total} (${coveragePercent}%)</span>
                <div class="tpl-var-progress-track">
                    <div class="tpl-var-progress-fill" style="width: ${coveragePercent}%"></div>
                </div>
            </div>
            <div class="tpl-var-grid">
                ${vars.map((v) => `
                    <div class="tpl-var-item ${v.filled ? "tpl-var-filled" : "tpl-var-empty-val"}">
                        <div class="tpl-var-key">\${${escapeHtml(v.key)}}</div>
                        <div class="tpl-var-value">${v.filled ? escapeHtml(v.value) : "—"}</div>
                    </div>
                `).join("")}
            </div>
        `;
    } catch (e) {
        panel.innerHTML = `<div class="tpl-var-empty">加载失败: ${escapeHtml(e.message)}</div>`;
        panel.dataset.loaded = "";
    }
}

async function showExpertDetail(expert) {
    const name = expert.displayName || expert.email || expert.orcidId || "?";
    const initial = name.charAt(0).toUpperCase();
    const contactDetail = $("#contactDetail");
    const tagLevel = expert.indexLevel || $("#expertIndexLevel").value || "CANDIDATE";
    const expertTags = expert.orcidId ? await fetchExpertTagsFromEs(expert.orcidId, tagLevel) : [];
    $("#contactHeadActions").hidden = true;
    $("#contactHeadActions").innerHTML = "";
    contactDetail.classList.remove("detail-empty");
    contactDetail.scrollTop = 0;
    contactDetail.innerHTML = `
        ${backToListBtnHtml()}
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

            ${expert.orcidId ? renderExpertTagEditor(expertTags, expert.orcidId, tagLevel) : ""}

            ${renderDetailSubTabs("academic")}

            <div class="detail-tab-panel" data-panel="academic">
                ${renderAcademicProfilePanel(expert)}
            </div>
            <div class="detail-tab-panel" data-panel="contact" hidden>
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
            <div class="detail-tab-panel" data-panel="template" hidden>
                <div class="tpl-var-empty">切换到本标签页以加载模板变量预览。</div>
            </div>
        </div>
    `;
    requestAnimationFrame(() => {
        contactDetail.scrollTop = 0;
        if (window.innerWidth <= 1024) {
            document.querySelector(".contact-detail-panel")?.scrollIntoView({ behavior: "smooth" });
        }
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

function pickTranslateSrc(mail) {
    const c = mail.cleanedBody;
    return (c && c.trim()) ? c : (mail.body || "");
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
            ${mail.sendStatus === "FAILED" && mail.errorSummary ? `
                <div class="mail-error-summary" style="margin-top: 4px; font-size: 0.9em; color: var(--color-status-error, #d93838);">
                    <strong>失败原因：</strong>${escapeHtml(mail.errorSummary)}
                </div>
            ` : ""}
            <div class="mail-preview">${escapeHtml(compactBody || "无正文")}</div>
            ${shouldCollapse ? `
                <details class="mail-body-detail">
                    <summary>查看完整正文</summary>
                    ${translatableBody(body, { translateSrc: pickTranslateSrc(mail) })}
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
            <div class="meeting-schedule-panel card span-all status-none">
                <div class="panel-header">
                    <div class="header-title">
                        <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        <h3>会议日程安排</h3>
                    </div>
                </div>
                <div class="panel-body empty-state">
                    <p>目前没有活动的会议安排。</p>
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
        <div class="meeting-schedule-panel card span-all ${statusClass}">
            <div class="panel-header">
                <div class="header-title">
                    <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    <h3>会议日程排期</h3>
                </div>
                <span class="badge ${isPending ? 'warn' : 'ok'}">${statusText}</span>
            </div>
            <div class="panel-body">
                ${activeSchedule.expertAvailableText ? `
                    <div class="form-group readonly">
                        <label>专家可沟通时间 (邮件提取)</label>
                        <div class="pre-text">${escapeHtml(activeSchedule.expertAvailableText)}</div>
                    </div>
                ` : ""}

                <form id="meetingScheduleForm" data-schedule-id="${activeSchedule.id}" data-contact-id="${contactId}">
                    <div class="form-row">
                        <div class="form-group flex-1">
                            <label for="chinaTime">中国时间 (China Time)</label>
                            <input type="text" id="chinaTime" name="chinaTime" value="${escapeHtml(activeSchedule.chinaTime || '')}" placeholder="例如: 2026-06-01 10:00 AM" ${!isPending ? 'disabled' : ''} required>
                        </div>
                        <div class="form-group flex-1">
                            <label for="meetingTool">会议工具</label>
                            <select id="meetingTool" name="meetingTool" ${!isPending ? 'disabled' : ''} required>
                                <option value="Zoom" ${activeSchedule.meetingTool === 'Zoom' ? 'selected' : ''}>Zoom</option>
                                <option value="Teams" ${activeSchedule.meetingTool === 'Teams' ? 'selected' : ''}>Teams</option>
                                <option value="Webex" ${activeSchedule.meetingTool === 'Webex' ? 'selected' : ''}>Webex</option>
                                <option value="Google Meet" ${activeSchedule.meetingTool === 'Google Meet' ? 'selected' : ''}>Google Meet</option>
                                <option value="Other" ${activeSchedule.meetingTool === 'Other' || (!activeSchedule.meetingTool && activeSchedule.meetingTool !== 'Zoom' && activeSchedule.meetingTool !== 'Teams' && activeSchedule.meetingTool !== 'Webex' && activeSchedule.meetingTool !== 'Google Meet') ? 'selected' : ''}>Other</option>
                            </select>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="meetingLink">会议链接 (Meeting Link)</label>
                        <input type="url" id="meetingLink" name="meetingLink" value="${escapeHtml(activeSchedule.meetingLink || '')}" placeholder="https://..." ${!isPending ? 'disabled' : ''} required>
                    </div>
                    <div class="form-group">
                        <label for="note">备注</label>
                        <textarea id="note" name="note" rows="2" placeholder="输入会议注意事项..." ${!isPending ? 'disabled' : ''}>${escapeHtml(activeSchedule.note || '')}</textarea>
                    </div>

                    <div class="panel-actions">
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
    const [detail, options, documents, logs] = await Promise.all([
        api(`/api/expert-contacts/${contactId}`),
        loadMailSendOptions(),
        api(`/api/expert-contacts/${contactId}/documents`).catch(() => []),
        api(`/api/operator-action-logs?expertContactId=${contactId}&pageSize=50&pageOffset=0`).catch(() => ({ records: [] }))
    ]);
    const contact = detail.contact;
    const expert = state.contacts.find(item => item.orcidId === state.selectedExpertOrcid) || {};
    const name = contact.expertName || contact.expertEmail || expert.displayName || "?";
    const initial = name.charAt(0).toUpperCase();
    $("#contactHeadActions").hidden = false;
    $("#contactHeadActions").innerHTML = `
        <div class="contact-head-status-row">
            <span class="contact-head-label">状态与层级:</span>
            <select id="operatorStatusSelect" data-contact-id="${contact.id}" data-original="${contact.operatorStatus || ""}" aria-label="专家状态">
                ${optionsFromArray(operatorStatusOptions, false, "请选择状态", contact.operatorStatus || "")}
            </select>
            <select id="indexLevelSelect" data-contact-id="${contact.id}" data-original="${contact.currentIndexLevel || ""}" aria-label="专家层级">
                ${optionsFromArray(indexLevelOptions, false, "请选择层级", contact.currentIndexLevel || "")}
            </select>
            <select id="autoReplySelect" data-contact-id="${contact.id}" data-original="${contact.autoReplyEnabled ? "auto" : "manual"}" aria-label="回复模式">
                <option value="auto" ${contact.autoReplyEnabled ? "selected" : ""}>自动回复</option>
                <option value="manual" ${!contact.autoReplyEnabled ? "selected" : ""}>人工回复</option>
            </select>
            <button class="button primary" id="saveContactChangesBtn" data-contact-id="${contact.id}" disabled>
                保存变更
            </button>
        </div>
        <div class="contact-head-mail-row">
            <span class="contact-head-label">手动发送邮件:</span>
            <select id="manualMailOption" aria-label="选择要发送的邮件">
                ${renderMailSendOptionGroups(options)}
            </select>
            <button class="button primary" data-action="send-manual-mail" data-id="${contact.id}">
                <span>发送邮件</span>
            </button>
        </div>
    `;

    const banner = renderManualAttentionBanner(contact);
    const contactDetail = $("#contactDetail");
    const tagLevel = contact.currentIndexLevel || expert.indexLevel || $("#expertIndexLevel").value || "CANDIDATE";
    const orcidId = contact.orcidId || expert.orcidId || "";
    const expertTags = orcidId ? await fetchExpertTagsFromEs(orcidId, tagLevel) : [];
    contactDetail.classList.remove("detail-empty");
    contactDetail.scrollTop = 0;
    contactDetail.innerHTML = `
        ${backToListBtnHtml()}
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

            ${renderExpertTagEditor(expertTags, orcidId, tagLevel)}

            ${renderDetailSubTabs("contact")}

            <div class="detail-tab-panel" data-panel="academic" hidden>
                ${renderAcademicProfilePanel(expert)}
            </div>
            <div class="detail-tab-panel" data-panel="contact">
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

                <div class="metadata-card span-all" id="expertDocumentsSection">
                    ${renderExpertDocuments(documents, contactId)}
                </div>

                <div class="metadata-card span-all" id="expertOperatorLogsSection">
                    ${renderOperatorLogs(logs)}
                </div>
            </div>
            </div>
            <div class="detail-tab-panel" data-panel="template" hidden>
                <div class="tpl-var-empty">切换到本标签页以加载模板变量预览。</div>
            </div>

        </div>
    `;
    requestAnimationFrame(() => {
        contactDetail.scrollTop = 0;
        if (window.innerWidth <= 1024) {
            document.querySelector(".contact-detail-panel")?.scrollIntoView({ behavior: "smooth" });
        }
    });
    if (contact.id) {
        loadEmailAliases(contact.id, contact);
    }
    return contact;
}

async function openContactInList(contactId) {
    setView("contacts");
    if (!state.contacts || state.contacts.length === 0) {
        await loadContacts();
    }
    const contact = await loadContactDetail(contactId);
    state.selectedExpertOrcid = contact?.orcidId || null;
    if (contact && !state.contacts.some(item => item.orcidId === contact.orcidId)) {
        state.contacts.unshift({
            orcidId: contact.orcidId,
            email: contact.expertEmail,
            displayName: contact.expertName,
            indexLevel: contact.currentIndexLevel,
            indexLevelName: indexLevelLabels[contact.currentIndexLevel] || contact.currentIndexLevel,
            contactId: contact.id,
            contactStatus: contact.currentStatus,
            operatorStatus: contact.operatorStatus,
            needsManualAttention: contact.needsManualAttention,
            country: "",
            employment: "",
            keyword: "",
            tags: contact.tags || [],
            updatedAt: contact.updatedAt || null
        });
    }
    renderContactListItems();
    document.querySelector("#contactList .list-item.active")?.scrollIntoView({ block: "nearest" });
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
const aiAnalysisState = {
    contactId: null,
    documents: [],
    results: [],
    mode: "select",
    error: null
};

function isDefaultAiAnalysisDocument(doc) {
    return AI_ANALYSIS_DEFAULT_TYPES.has(doc.documentType);
}

async function openAiAnalysisModal(contactId) {
    aiAnalysisState.contactId = contactId;
    aiAnalysisState.error = null;
    aiAnalysisState.documents = await api(`/api/expert-contacts/${contactId}/documents`).catch(() => []);
    const existing = await api(`/api/expert-contacts/${contactId}/ai-analysis`).catch(() => ({ fields: [] }));
    aiAnalysisState.results = existing.fields || [];
    aiAnalysisState.mode = aiAnalysisState.results.length > 0 ? "results" : "select";
    renderAiAnalysisModal();
    const modal = $("#aiAnalysisModal");
    if (modal) modal.hidden = false;
}

function closeAiAnalysisModal() {
    const modal = $("#aiAnalysisModal");
    if (modal) modal.hidden = true;
    aiAnalysisState.contactId = null;
    aiAnalysisState.documents = [];
    aiAnalysisState.results = [];
    aiAnalysisState.mode = "select";
    aiAnalysisState.error = null;
}

function renderAiAnalysisFileSelect() {
    const docs = Array.isArray(aiAnalysisState.documents) ? aiAnalysisState.documents : [];
    if (docs.length === 0) {
        return `<p class="ai-analysis-empty">暂无可用资料文件。</p>`;
    }
    return `
        <p class="ai-analysis-hint">选择要分析的文件（默认勾选 CV 与学位类文件）：</p>
        <div class="ai-analysis-file-list">
            ${docs.map(doc => `
                <label class="ai-analysis-file-item">
                    <input type="checkbox" name="aiAnalysisAttachment" value="${doc.attachmentId}"
                        ${isDefaultAiAnalysisDocument(doc) ? "checked" : ""}>
                    <span class="ai-analysis-file-name">${escapeHtml(doc.fileName || "?")}</span>
                    <span class="badge">${escapeHtml(labelDocumentType(doc.documentType))}</span>
                </label>
            `).join("")}
        </div>
    `;
}

function renderAiAnalysisResults() {
    const fields = aiAnalysisState.results || [];
    if (fields.length === 0) {
        return `<p class="ai-analysis-empty">暂无分析结果。</p>`;
    }
    return `
        <div class="analysis-field-list">
            ${fields.map(field => `
                <div class="analysis-field-row" data-field-id="${field.id}">
                    <label class="analysis-field-label">${escapeHtml(field.fieldLabel || field.fieldKey)}</label>
                    <div class="analysis-field-input-wrap">
                        <input class="analysis-field-input" type="text"
                            data-action="ai-analysis-field-input"
                            data-field-id="${field.id}"
                            value="${escapeHtml(field.value || "")}">
                        ${field.sourceFileName ? `
                            <span class="source-badge ${field.verified ? "" : "source-badge-warn"}"
                                title="${escapeHtml(field.sourceExcerpt || "")}">
                                ${field.verified ? "" : "⚠ "}${escapeHtml(field.sourceFileName)}
                            </span>
                        ` : ""}
                    </div>
                </div>
            `).join("")}
        </div>
    `;
}

function renderAiAnalysisModal() {
    const body = $("#aiAnalysisModalBody");
    const title = $("#aiAnalysisModalTitle");
    const footer = $("#aiAnalysisModalFooter");
    if (!body || !title || !footer) return;

    if (aiAnalysisState.mode === "loading") {
        title.textContent = "AI 智能分析";
        body.innerHTML = `
            <div class="ai-analysis-loading">
                <div class="ai-analysis-spinner"></div>
                <p>正在分析文档，请稍候…</p>
            </div>
        `;
        footer.innerHTML = `<button type="button" class="button secondary" data-action="close-ai-analysis">关闭</button>`;
        return;
    }

    if (aiAnalysisState.mode === "results") {
        title.textContent = "AI 分析结果";
        body.innerHTML = aiAnalysisState.error
            ? `<p class="ai-analysis-error">${escapeHtml(aiAnalysisState.error)}</p>${renderAiAnalysisResults()}`
            : renderAiAnalysisResults();
        footer.innerHTML = `
            <button type="button" class="button secondary" data-action="ai-analysis-add-field">+ 添加字段</button>
            <button type="button" class="button secondary" data-action="ai-analysis-reanalyze">重新分析</button>
            <button type="button" class="button secondary" data-action="close-ai-analysis">关闭</button>
        `;
        return;
    }

    title.textContent = "选择分析文件";
    body.innerHTML = aiAnalysisState.error
        ? `<p class="ai-analysis-error">${escapeHtml(aiAnalysisState.error)}</p>${renderAiAnalysisFileSelect()}`
        : renderAiAnalysisFileSelect();
    footer.innerHTML = `
        <button type="button" class="button primary" data-action="start-ai-analysis">开始分析</button>
        <button type="button" class="button secondary" data-action="close-ai-analysis">取消</button>
    `;
}

async function startAiAnalysis() {
    const contactId = aiAnalysisState.contactId;
    if (!contactId) return;
    const checked = Array.from(document.querySelectorAll('input[name="aiAnalysisAttachment"]:checked'))
        .map(el => Number(el.value))
        .filter(id => Number.isFinite(id));
    if (checked.length === 0) {
        aiAnalysisState.error = "请至少选择一个文件";
        renderAiAnalysisModal();
        return;
    }
    aiAnalysisState.error = null;
    aiAnalysisState.mode = "loading";
    renderAiAnalysisModal();
    try {
        const result = await api(`/api/expert-contacts/${contactId}/ai-analysis`, {
            method: "POST",
            body: JSON.stringify({ attachmentIds: checked })
        });
        aiAnalysisState.results = result.fields || [];
        aiAnalysisState.mode = "results";
        showStatus("AI 分析完成");
    } catch (e) {
        aiAnalysisState.mode = "select";
        aiAnalysisState.error = e.message || "分析失败，请重试";
    }
    renderAiAnalysisModal();
}

async function saveAiAnalysisField(fieldId, value) {
    const contactId = aiAnalysisState.contactId;
    if (!contactId || !fieldId) return;
    try {
        const updated = await api(`/api/expert-contacts/${contactId}/ai-analysis/${fieldId}`, {
            method: "PUT",
            body: JSON.stringify({ value })
        });
        const idx = aiAnalysisState.results.findIndex(item => item.id === fieldId);
        if (idx >= 0) {
            aiAnalysisState.results[idx] = updated;
        }
    } catch (e) {
        showStatus(e.message || "保存失败", "error");
    }
}

async function addAiAnalysisField() {
    const contactId = aiAnalysisState.contactId;
    if (!contactId) return;
    const fieldKey = window.prompt("字段 key（英文，如 custom_note）");
    if (!fieldKey) return;
    const fieldLabel = window.prompt("字段名称（中文显示名）");
    if (!fieldLabel) return;
    const value = window.prompt("字段值") || "";
    try {
        const created = await api(`/api/expert-contacts/${contactId}/ai-analysis/fields`, {
            method: "POST",
            body: JSON.stringify({ fieldKey, fieldLabel, value })
        });
        aiAnalysisState.results.push(created);
        renderAiAnalysisModal();
    } catch (e) {
        showStatus(e.message || "添加字段失败", "error");
    }
}

function renderOperatorLogs(logs) {
    const list = Array.isArray(logs) ? logs : (logs?.records || []);
    if (list.length === 0) {
        return `
            <div class="metadata-card-header">
                <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                <span>操作日志 (最近50条)</span>
            </div>
            <p style="color: var(--text-muted); font-size: 12px;">暂无操作日志。</p>
        `;
    }
    return `
        <div class="metadata-card-header">
            <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
            <span>操作日志 (最近50条)</span>
        </div>
        <div class="log-list">
            ${list.map(log => {
                const detail = renderLogDetail(log);
                return `
                <div class="log-row">
                    <div>
                        <strong>${actionTypeLabel(log.actionType)}</strong>
                        <span>${escapeHtml(log.operatorName || "-")}&nbsp;·&nbsp;${escapeHtml(log.createdAt || "")}</span>
                        ${log.note ? `<span style="color:var(--text-muted);font-size:12px">${escapeHtml(log.note)}</span>` : ""}
                        ${detail}
                    </div>
                </div>
            `}).join("")}
        </div>
    `;
}

function renderLogDetail(log) {
    const before = tryParseJson(log.beforeValue);
    const after = tryParseJson(log.afterValue);
    switch (log.actionType) {
        case "SEND_MANUAL_RICH_REPLY":
            return `<details class="log-detail"><summary>回复详情</summary>
                <div><strong>主题:</strong> ${escapeHtml(after?.subject || "-")}</div>
                <div><strong>内容:</strong><br>${escapeHtml(after?.bodyPreviewText || after?.bodyPreview || "-")}</div>
                <div><strong>发送状态:</strong> ${escapeHtml(after?.sendStatus || "-")}</div>
            </details>`;
        case "SEND_QA_REPLY":
            return `<details class="log-detail"><summary>QA 回复详情</summary>
                <div><strong>QA 规则:</strong> ${escapeHtml(after?.qaRuleName || after?.qaRuleId || "-")}</div>
                <div><strong>主题:</strong> ${escapeHtml(after?.subject || "-")}</div>
                <div><strong>内容:</strong><br>${escapeHtml(after?.bodyPreviewText || after?.bodyPreview || "-")}</div>
                <div><strong>发送状态:</strong> ${escapeHtml(after?.sendStatus || "-")}</div>
            </details>`;
        case "CHANGE_OPERATOR_STATUS":
            return `<div class="log-transition">
                ${operatorStatusLabels[before?.operatorStatus] || before?.operatorStatus || "?"}
                → ${operatorStatusLabels[after?.operatorStatus] || after?.operatorStatus || "?"}
            </div>`;
        case "CHANGE_INDEX_LEVEL":
            return `<div class="log-transition">
                ${indexLevelLabels[before?.currentIndexLevel] || before?.currentIndexLevel || "?"}
                → ${indexLevelLabels[after?.currentIndexLevel] || after?.currentIndexLevel || "?"}
            </div>`;
        case "SWITCH_REPLY_MODE": {
            const beforeMode = before?.autoReplyEnabled === true || before?.autoReplyEnabled === "true" ? "自动回复" : "人工回复";
            const afterMode = after?.autoReplyEnabled === true || after?.autoReplyEnabled === "true" ? "自动回复" : "人工回复";
            const beforeStatus = labelStatus(before?.currentStatus) || "?";
            const afterStatus = labelStatus(after?.currentStatus) || "?";
            return `<div class="log-transition">
                回复模式: ${beforeMode} → ${afterMode}
                ${before?.currentStatus !== after?.currentStatus
                    ? `<br>状态: ${beforeStatus} → ${afterStatus}`
                    : ""}
            </div>`;
        }
        case "MARK_INBOUND_RESOLVED": {
            return `<div class="log-transition">
                ${labelStatus(before?.processStatus) || "?"} → ${labelStatus(after?.processStatus) || "?"}
            </div>`;
        }
        case "AI_REPLY_DRAFT_READY":
        case "AI_REPLY_DRAFT_NEEDS_REVIEW":
        case "AI_REPLY_DRAFT_BLOCKED": {
            const hasNewSchema = after?.schemaVersion === "ai-reply-draft-audit-v1";
            if (hasNewSchema) {
                const readinessLabel = after?.readiness === "READY" ? "依据完整" : after?.readiness === "NEEDS_REVIEW" ? "需补充" : "缺依据";
                const modeLabel = after?.mode === "QA_MATCHED" ? "规则拼接" : after?.mode === "QA_GROUNDED" ? "信任求实" : "自由生成";
                const genLabel = after?.generationState === "LLM_USED" ? "LLM 已生成" : after?.generationState || "";
                const shortHash = (after?.draftHash || "").substring(0, 12);
                const shortEvidence = shortEvidenceHash(after?.evidenceSetVersion || "");
                return `<details class="log-detail"><summary>AI 草稿审计</summary>
                    <div>状态：${escapeHtml(readinessLabel)} · 模式：${escapeHtml(modeLabel)} · 模型：${escapeHtml(after?.model || "-")}</div>
                    <div>Prompt 版本：${escapeHtml(after?.promptVersion || "-")} · 草稿哈希：${escapeHtml(shortHash)}</div>
                    <div>证据集版本：${escapeHtml(shortEvidence)} · 来源数：${escapeHtml(String(after?.sourceTotal || 0))}${after?.sourceTruncated ? "（已截断）" : ""}</div>
                    <div>覆盖：${escapeHtml(String(after?.groundedRequestCount || 0))}/${escapeHtml(String(after?.requestCount || 0))}${after?.coverageTruncated ? "（已截断）" : ""} · 生成：${escapeHtml(genLabel)}</div>
                    ${after?.warningTotal > 0 ? `<div>风险警告：${escapeHtml(String(after?.warningTotal))} 条${after?.warningTruncated ? "（已截断）" : ""}</div>` : ""}
                </details>`;
            }
            const oldReadiness = after?.readiness || "READY";
            const oldLabel = oldReadiness === "READY" ? "依据完整" : oldReadiness === "NEEDS_REVIEW" ? "需补充" : "缺依据";
            return `<details class="log-detail"><summary>AI 草稿审计</summary>
                <div>模型：${escapeHtml(after?.model || "-")} · 模式：${escapeHtml(after?.mode || "-")} · 状态：${escapeHtml(oldLabel)}</div>
                <div>覆盖：${escapeHtml(String(after?.groundedRequestCount || 0))}/${escapeHtml(String(after?.requestCount || 0))} · 生成：${escapeHtml(after?.generationState || "-")}</div>
            </details>`;
        }
        default:
            return (before || after) ? `
                <details class="log-detail">
                    <summary>变更详情</summary>
                    ${before ? `<div>前: ${escapeHtml(JSON.stringify(before))}</div>` : ""}
                    ${after ? `<div>后: ${escapeHtml(JSON.stringify(after))}</div>` : ""}
                </details>
            ` : "";
    }
}

function tryParseJson(str) {
    if (!str) return null;
    try { return JSON.parse(str); } catch { return null; }
}

function actionTypeLabel(type) {
    const map = {
        CHANGE_OPERATOR_STATUS: "变更专家状态",
        CHANGE_INDEX_LEVEL: "变更专家层级",
        SWITCH_REPLY_MODE: "切换回复模式",
        BIND_INBOUND_MAIL: "绑定待处理邮件",
        SEND_QA_REPLY: "发送 QA 邮件",
        SEND_MANUAL_RICH_REPLY: "人工回复邮件",
        SEND_MANUAL_COMPOSED_REPLY: "组装 QA 回复",
        MARK_INBOUND_RESOLVED: "标记已处理"
    };
    return map[type] || type;
}

async function loadMailSendOptions() {
    if (state.mailSendOptions.length > 0) {
        return state.mailSendOptions;
    }
    state.mailSendOptions = await api("/api/expert-contacts/mail-send-options");
    return state.mailSendOptions;
}

function renderMailSendOptionGroups(options) {
    const mailTemplates = options.filter((option) => option.optionType === "COMPOSE_TEMPLATE");
    const renderOption = (option) => `
        <option value="${escapeHtml(option.optionType)}:${escapeHtml(option.optionValue)}">
            ${escapeHtml(option.optionName)}${option.subject ? ` - ${escapeHtml(option.subject)}` : ""}
        </option>`;
    return mailTemplates.map(renderOption).join("") || `<option value="">无可用模板</option>`;
}

async function loadMailTemplatesView() {
    await Promise.all([loadQa(), loadReplySnippets(), loadComposeTemplates()]);
    switchMailTemplatesSubTab(state.mailTemplatesSubTab || "qa");
}

function switchMailTemplatesSubTab(tab) {
    state.mailTemplatesSubTab = tab;
    document.querySelectorAll("#view-mail-templates .mail-templates-tab").forEach((button) => {
        button.classList.toggle("active", button.dataset.subTab === tab);
    });
    const panelMap = {
        qa: "mailTemplatesPanelQa",
        "reply-snippets": "mailTemplatesPanelReplySnippets",
        "compose-templates": "mailTemplatesPanelComposeTemplates"
    };
    document.querySelectorAll("#view-mail-templates .mail-templates-panel").forEach((panel) => {
        panel.classList.toggle("active", panel.id === panelMap[tab]);
    });
}

const composeBlockTypeLabels = {
    QA_RULE: "QA 规则",
    REPLY_SNIPPET: "回复片段",
    CUSTOM_TEXT: "自定义文本"
};

let composeTemplatePreviewRequestId = 0;

function composeTemplatePreviewExpertLabel(expert) {
    const name = expert.expertName || expert.displayName || expert.name || "未命名专家";
    const email = expert.expertEmail || expert.email || "";
    return email ? `${name} <${email}>` : name;
}

function composeTemplatePreviewAccountLabel(account) {
    const code = account.accountCode || account.senderEmail || "邮箱账号";
    const email = account.senderEmail || "";
    return email ? `${code} <${email}>` : code;
}

function findComposeTemplatePreviewOption(items, inputValue, labelFn) {
    const value = String(inputValue || "").trim();
    if (!value) return null;
    return items.find((item) => labelFn(item) === value)
        || items.find((item) => labelFn(item).toLowerCase().includes(value.toLowerCase()))
        || null;
}

function collectComposeTemplatePreviewContext() {
    const expertInput = $("#previewComposeExpertInput")?.value;
    const accountInput = $("#previewComposeAccountInput")?.value;
    const expert = findComposeTemplatePreviewOption(
        state.composeTemplatePreviewExperts || [],
        expertInput,
        composeTemplatePreviewExpertLabel
    );
    const account = findComposeTemplatePreviewOption(
        state.composeTemplatePreviewAccounts || [],
        accountInput,
        composeTemplatePreviewAccountLabel
    );
    return {
        contactId: expert?.contactId ?? expert?.id ?? null,
        orcidId: expert?.orcidId || state.previewDrawer.orcidId || null,
        expertEmail: expert?.expertEmail || expert?.email || state.previewDrawer.expertEmail || null,
        senderAccountCode: account?.accountCode || null
    };
}

function collectComposeTemplatePreviewSampleText() {
    const form = $("#composeTemplateForm");
    const parts = [form?.subject?.value || ""];
    collectComposeTemplateBlocksFromForm().forEach((block) => {
        if (block.blockType === "CUSTOM_TEXT") {
            parts.push(block.customText || "");
            return;
        }
        if (block.blockType === "QA_RULE") {
            const rule = state.qaRules.find((item) => Number(item.id) === Number(block.refId));
            if (rule?.replyBody) parts.push(rule.replyBody);
            return;
        }
        if (block.blockType === "REPLY_SNIPPET") {
            const snippet = (state.replySnippets || []).find((item) => Number(item.id) === Number(block.refId));
            if (snippet?.content) parts.push(snippet.content);
        }
    });
    return parts.filter((text) => String(text).trim()).join("\n");
}

function renderContentVariantRows(container, variants) {
    if (!container) return;
    const values = variants || [];
    if (!values.length) {
        container.dataset.activeIndex = "0";
        container.innerHTML = `
            <div class="content-variant-carousel">
                <p class="content-variants-empty">未添加变体，仅使用主体发送</p>
                <div class="content-variant-nav">
                    <span class="content-variant-nav-spacer"></span>
                    <button type="button" class="button small" data-action="add-content-variant">+ 新增</button>
                </div>
            </div>`;
        updateContentVariantsCountBadge(container);
        return;
    }
    const n = values.length;
    let active = Number(container.dataset.activeIndex);
    if (!Number.isFinite(active)) active = 0;
    active = Math.max(0, Math.min(active, n - 1));
    container.dataset.activeIndex = String(active);
    const rows = values.map((value, index) => {
        const isActive = index === active;
        return `
        <div class="content-variant-row${isActive ? " active" : ""}" data-variant-index="${index}"${isActive ? "" : " hidden"}>
            <span class="content-variant-index">${index + 1}</span>
            <textarea class="content-variant-input" rows="3" maxlength="2000" placeholder="变体正文">${escapeHtml(value || "")}</textarea>
            <button type="button" class="button small danger" data-action="remove-content-variant" data-index="${index}">×</button>
        </div>`;
    }).join("");
    const dots = values.map((_, index) =>
        `<span class="content-variant-dot${index === active ? " active" : ""}" data-index="${index}"></span>`
    ).join("");
    container.innerHTML = `
        <div class="content-variant-carousel">
            <div class="content-variant-nav">
                <button type="button" class="button small" data-action="variant-prev" aria-label="上一个变体">‹</button>
                <span class="content-variant-nav-counter">${active + 1} / ${n}</span>
                <button type="button" class="button small" data-action="variant-next" aria-label="下一个变体">›</button>
                <span class="content-variant-nav-spacer"></span>
                <button type="button" class="button small" data-action="add-content-variant">+ 新增</button>
            </div>
            <div class="content-variant-rows">
                ${rows}
            </div>
            <div class="content-variant-dots">
                ${dots}
            </div>
        </div>`;
    updateContentVariantsCountBadge(container);
}

function setActiveVariant(container, index) {
    if (!container) return;
    const rows = Array.from(container.querySelectorAll(".content-variant-row"));
    const n = rows.length;
    if (!n) return;
    let active = Number(index);
    if (!Number.isFinite(active)) active = 0;
    active = Math.max(0, Math.min(active, n - 1));
    container.dataset.activeIndex = String(active);
    rows.forEach((row, i) => {
        const isActive = i === active;
        row.classList.toggle("active", isActive);
        if (isActive) {
            row.removeAttribute("hidden");
        } else {
            row.setAttribute("hidden", "");
        }
        const hint = row.nextElementSibling;
        if (hint?.classList.contains("content-variant-duplicate-hint")) {
            if (isActive) {
                hint.removeAttribute("hidden");
            } else {
                hint.setAttribute("hidden", "");
            }
        }
    });
    const counter = container.querySelector(".content-variant-nav-counter");
    if (counter) counter.textContent = `${active + 1} / ${n}`;
    container.querySelectorAll(".content-variant-dot").forEach((dot) => {
        dot.classList.toggle("active", Number(dot.dataset.index) === active);
    });
}

function updateContentVariantsCountBadge(container) {
    if (!container) return;
    const badge = container.closest(".content-variants-block")?.querySelector(".content-variants-count");
    if (!badge) return;
    const variantCount = collectContentVariants(container).length;
    if (variantCount > 0) {
        badge.hidden = false;
        badge.textContent = `${variantCount} 变体`;
    } else {
        badge.hidden = true;
        badge.textContent = "";
    }
}

function collectContentVariants(container) {
    if (!container) return [];
    return Array.from(container.querySelectorAll(".content-variant-input"))
        .map((input) => input.value.trim())
        .filter(Boolean);
}

function clearContentVariantValidationMarks(container) {
    if (!container) return;
    container.querySelectorAll(".content-variant-duplicate-hint").forEach((element) => element.remove());
    container.querySelectorAll(".content-variant-input.duplicate").forEach((input) => {
        input.classList.remove("duplicate");
    });
}

function validateContentVariantInputs(container, mainText) {
    if (!container) return true;
    clearContentVariantValidationMarks(container);
    const trimmedMain = (mainText || "").trim();
    const inputs = Array.from(container.querySelectorAll(".content-variant-input"));
    if (!inputs.length) return true;
    const trimmedValues = inputs.map((input) => input.value.trim());
    let valid = true;
    const seen = new Map();

    trimmedValues.forEach((value, index) => {
        const row = inputs[index].closest(".content-variant-row");
        const insertHint = (message) => {
            const hint = document.createElement("p");
            hint.className = "content-variant-duplicate-hint";
            hint.textContent = message;
            row?.insertAdjacentElement("afterend", hint);
        };
        if (!value) {
            inputs[index].classList.add("duplicate");
            insertHint("变体不能为空");
            valid = false;
            return;
        }
        if (value === trimmedMain) {
            inputs[index].classList.add("duplicate");
            insertHint("与主体 内容重复");
            valid = false;
            return;
        }
        if (seen.has(value)) {
            inputs[index].classList.add("duplicate");
            insertHint(`与变体 ${seen.get(value)} 内容重复`);
            valid = false;
            return;
        }
        seen.set(value, index + 1);
    });

    if (!valid) {
        showStatus("请修正内容变体中的重复或空值", "error");
        const firstDuplicate = container.querySelector(".content-variant-input.duplicate");
        const row = firstDuplicate?.closest(".content-variant-row");
        if (row) {
            const invalidIndex = Number(row.dataset.variantIndex);
            if (Number.isFinite(invalidIndex)) setActiveVariant(container, invalidIndex);
        }
    }
    return valid;
}

function addContentVariantRow(container) {
    if (!container) return;
    const variants = Array.from(container.querySelectorAll(".content-variant-input")).map((input) => input.value);
    variants.push("");
    container.dataset.activeIndex = String(variants.length - 1);
    renderContentVariantRows(container, variants);
    container.querySelector(".content-variant-row.active .content-variant-input")?.focus();
}

function removeContentVariantRow(container, index) {
    if (!container) return;
    const variants = Array.from(container.querySelectorAll(".content-variant-input")).map((input) => input.value);
    variants.splice(index, 1);
    const n = variants.length;
    container.dataset.activeIndex = n === 0 ? "0" : String(Math.min(index, n - 1));
    renderContentVariantRows(container, variants);
}

function handleContentVariantEditorClick(event, form) {
    const addBtn = event.target.closest('[data-action="add-content-variant"]');
    if (addBtn && form.contains(addBtn)) {
        const container = addBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
        if (container) addContentVariantRow(container);
        return;
    }
    const removeBtn = event.target.closest('[data-action="remove-content-variant"]');
    if (removeBtn && form.contains(removeBtn)) {
        const container = removeBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
        if (container) removeContentVariantRow(container, Number(removeBtn.dataset.index));
        return;
    }
    const prevBtn = event.target.closest('[data-action="variant-prev"]');
    if (prevBtn && form.contains(prevBtn)) {
        const container = prevBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
        if (container) setActiveVariant(container, Number(container.dataset.activeIndex || 0) - 1);
        return;
    }
    const nextBtn = event.target.closest('[data-action="variant-next"]');
    if (nextBtn && form.contains(nextBtn)) {
        const container = nextBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
        if (container) setActiveVariant(container, Number(container.dataset.activeIndex || 0) + 1);
        return;
    }
    const dot = event.target.closest(".content-variant-dot[data-index]");
    if (dot && form.contains(dot)) {
        const container = dot.closest(".content-variants-block")?.querySelector(".content-variants-container");
        if (container) setActiveVariant(container, Number(dot.dataset.index));
    }
}

function handleContentVariantEditorInput(event) {
    if (!event.target.classList.contains("content-variant-input")) return;
    const container = event.target.closest(".content-variants-container");
    if (container) updateContentVariantsCountBadge(container);
}

function populateComposeTemplatePreviewDatalists() {
    const expertList = $("#previewComposeExpertOptions");
    if (expertList) {
        expertList.innerHTML = (state.composeTemplatePreviewExperts || [])
            .map((expert) => `<option value="${escapeHtml(composeTemplatePreviewExpertLabel(expert))}"></option>`)
            .join("");
    }
    const accountList = $("#previewComposeAccountOptions");
    if (accountList) {
        accountList.innerHTML = (state.composeTemplatePreviewAccounts || [])
            .map((account) => `<option value="${escapeHtml(composeTemplatePreviewAccountLabel(account))}"></option>`)
            .join("");
    }
}

async function loadComposeTemplatePreviewOptions() {
    if (state.composeTemplatePreviewOptionsLoaded) return;
    const [contactsResult, accounts] = await Promise.all([
        api("/api/expert-contacts").catch(() => ({ contacts: [] })),
        api("/api/mail/sender-accounts").catch(() => [])
    ]);
    state.composeTemplatePreviewExperts = Array.isArray(contactsResult)
        ? contactsResult
        : contactsResult.contacts || [];
    state.composeTemplatePreviewAccounts = Array.isArray(accounts) ? accounts : [];
    state.composeTemplatePreviewOptionsLoaded = true;
    populateComposeTemplatePreviewDatalists();
}

async function loadComposeTemplates() {
    state.composeTemplates = await api("/api/compose-templates");
    renderComposeTemplatesTable();
}

function renderComposeTemplatesTable() {
    const table = $("#composeTemplatesTable");
    if (!table) return;
    table.innerHTML = state.composeTemplates.map((template) => {
        const blockPills = (template.blocks || []).map((block) => {
            const label = block.refDisplayName
                || (block.blockType === "CUSTOM_TEXT" ? "自定义文本" : composeBlockTypeLabels[block.blockType] || block.blockType);
            return `<span class="compose-block-pill">${escapeHtml(label)}</span>`;
        }).join("");
        return `
        <tr>
            <td><strong>${escapeHtml(template.templateName)}</strong></td>
            <td>${escapeHtml(template.subject)}</td>
            <td>${blockPills || '<span class="muted">无内容块</span>'}</td>
            <td>${badge(template.enabled ? "启用" : "禁用", template.enabled ? "ok" : "warn")}</td>
            <td style="text-align: right; white-space: nowrap;">
                <button type="button" class="button small" data-action="edit-compose-template" data-id="${template.id}">编辑</button>
                <button type="button" class="button small" data-action="preview-compose-template" data-id="${template.id}">预览</button>
                <button type="button" class="button small" data-action="toggle-compose-template" data-id="${template.id}" data-enabled="${template.enabled}">${template.enabled ? "禁用" : "启用"}</button>
                <button type="button" class="button small" data-action="delete-compose-template" data-id="${template.id}">删除</button>
            </td>
        </tr>`;
    }).join("") || `<tr><td colspan="5" class="muted" style="text-align:center;padding:20px;">暂无邮件模板</td></tr>`;
}

function openComposeTemplateEditor(template) {
    state.selectedComposeTemplateId = template?.id ?? null;
    const form = $("#composeTemplateForm");
    form.templateName.value = template?.templateName || "";
    form.subject.value = template?.subject || "";
    form.description.value = template?.description || "";
    form.enabled.checked = template?.enabled !== false;
    $("#composeTemplateEditorTitle").textContent = template ? "编辑邮件模板" : "新建邮件模板";
    renderComposeTemplateBlockRows(template?.blocks || []);
    loadComposeTemplatePreviewOptions().catch((error) => showStatus(error.message, "error"));
    $("#composeTemplateModal").hidden = false;
    refreshVariableEditors().catch((error) => showStatus(error.message, "error"));
    mountPreviewRail({ targetId: "composeTemplate" });
}

function hideComposeTemplateEditor() {
    $("#composeTemplateModal").hidden = true;
    state.selectedComposeTemplateId = null;
    closePreviewDrawer();
}

function renderComposeTemplateBlockRows(blocks) {
    const container = $("#composeTemplateBlocksList");
    if (!container) return;
    const rows = (blocks.length ? blocks : [{ blockType: "CUSTOM_TEXT", blockOrder: 0 }]).map((block, index) =>
        composeTemplateBlockRowHtml(index, block)
    );
    container.innerHTML = rows.join("");
    refreshVariableEditors().catch(() => {});
}

function composeTemplateBlockRowHtml(index, block) {
    const blockType = block.blockType || "CUSTOM_TEXT";
    const enabledSnippets = (state.replySnippets || []).filter((snippet) => snippet.enabled);
    const snippetOptions = enabledSnippets.map((snippet) => {
        const label = `${replySnippetTypeLabels[snippet.snippetType] || snippet.snippetType} #${snippet.id}`;
        return `<option value="${snippet.id}" ${String(block.refId) === String(snippet.id) ? "selected" : ""}>${escapeHtml(label)}</option>`;
    }).join("");
    return `
    <div class="compose-template-block-row" data-block-index="${index}" draggable="true">
        <span class="block-drag-handle">⋮⋮</span>
        <span class="block-order">#${index + 1}</span>
        <select class="block-type-select" data-field="blockType">
            <option value="REPLY_SNIPPET" ${blockType === "REPLY_SNIPPET" ? "selected" : ""}>回复片段</option>
            <option value="CUSTOM_TEXT" ${blockType === "CUSTOM_TEXT" ? "selected" : ""}>自定义文本</option>
        </select>
        <div class="block-ref">
            ${blockType === "REPLY_SNIPPET" ? `<select data-field="refId"><option value="">请选择回复片段</option>${snippetOptions}</select>` : ""}
            ${blockType === "CUSTOM_TEXT" ? `<div class="var-editor-wrap"><div class="var-editor-toolbar"><div class="var-insert-wrap"><button type="button" class="var-insert-btn" data-var-insert-target="composeBlockCustomText-${index}">+ 插入变量 ▾</button><div class="var-insert-menu" hidden></div></div></div><textarea id="composeBlockCustomText-${index}" data-field="customText" rows="4" placeholder="输入自定义文本">${escapeHtml(block.customText || "")}</textarea></div>` : ""}
        </div>
        <div class="block-actions">
            <button type="button" class="button small" data-action="move-compose-block-up" data-index="${index}" ${index === 0 ? "disabled" : ""}>↑</button>
            <button type="button" class="button small" data-action="move-compose-block-down" data-index="${index}">↓</button>
            <button type="button" class="button small danger" data-action="remove-compose-block" data-index="${index}">×</button>
        </div>
    </div>`;
}

function collectComposeTemplateBlocksFromForm() {
    const rows = $$("#composeTemplateBlocksList .compose-template-block-row");
    return rows.map((row, index) => {
        const blockType = row.querySelector('[data-field="blockType"]')?.value || "CUSTOM_TEXT";
        const refIdRaw = row.querySelector('[data-field="refId"]')?.value;
        const customText = row.querySelector('[data-field="customText"]')?.value || "";
        return {
            blockOrder: index,
            blockType,
            refId: refIdRaw ? Number(refIdRaw) : null,
            customText: blockType === "CUSTOM_TEXT" ? customText : null
        };
    });
}

async function refreshComposeTemplatePreview() {
    await renderServerComposeTemplatePreview();
}

function renderComposeTemplatePreviewVariableRows(variables) {
    return (variables || []).map((item) => {
        const dotClass = item.usedFallback ? "fallback" : item.filled ? "filled" : "fallback";
        return `<div class="preview-var-row">
            <span class="preview-var-dot ${dotClass}"></span>
            <span class="preview-var-key">${escapeHtml(item.key)}</span>
            <span class="preview-var-label">${escapeHtml(item.label)}</span>
            <span class="preview-var-value" title="${escapeHtml(item.value || "")}">${escapeHtml(item.value || "—")}</span>
        </div>`;
    }).join("");
}

function renderComposeTemplatePreviewInDrawer(preview) {
    const errorEl = $("#previewDrawerError");
    if (errorEl) {
        errorEl.hidden = true;
        errorEl.textContent = "";
    }
    const toEl = $("#previewMailTo");
    const subjectEl = $("#previewMailSubject");
    const bodyEl = $("#previewMailBody");
    const rowsEl = $("#previewVarRows");
    const statEl = $("#previewVarStat");
    const blockNotesEl = $("#previewComposeBlockNotes");
    const skippedEl = $("#previewComposeSkipped");
    const toEmail = preview.toEmail || "—";
    if (toEl) toEl.textContent = toEmail;
    if (subjectEl) subjectEl.textContent = preview.subject || "—";
    const blockNotes = (preview.blocks || []).map((block) => {
        const label = block.refDisplayName || composeBlockTypeLabels[block.blockType] || block.blockType;
        if (!block.included) {
            return `<div class="compose-block-pill skipped">#${block.blockOrder + 1} ${escapeHtml(label)} — 已跳过${block.skipReason ? `（${escapeHtml(block.skipReason)}）` : ""}</div>`;
        }
        return `<div class="compose-block-pill">#${block.blockOrder + 1} ${escapeHtml(label)}</div>`;
    }).join("");
    if (blockNotesEl) {
        if (blockNotes) {
            blockNotesEl.hidden = false;
            blockNotesEl.innerHTML = blockNotes;
        } else {
            blockNotesEl.hidden = true;
            blockNotesEl.innerHTML = "";
        }
    }
    const strictSkippedCount = (preview.blocks || []).filter(
        (block) => !block.included && block.skipReason === "存在未满足占位符"
    ).length;
    if (skippedEl) {
        if (strictSkippedCount > 0) {
            skippedEl.hidden = false;
            skippedEl.textContent = `已跳过 ${strictSkippedCount} 段：存在未满足占位符`;
        } else {
            skippedEl.hidden = true;
            skippedEl.textContent = "";
        }
    }
    if (bodyEl) {
        bodyEl.textContent = preview.body || "添加内容块后显示预览。";
    }
    const variables = preview.variables || [];
    let filledCount = 0;
    let fallbackCount = 0;
    variables.forEach((item) => {
        if (item.usedFallback) fallbackCount += 1;
        else if (item.filled) filledCount += 1;
        else fallbackCount += 1;
    });
    if (statEl) {
        statEl.textContent = `${filledCount} 有值 · ${fallbackCount} 兜底 · 0 非法`;
    }
    if (rowsEl) {
        rowsEl.innerHTML = renderComposeTemplatePreviewVariableRows(variables);
    }
    updatePreviewCoverage(null, null);
    updatePreviewVariantSwitcher(preview.variantPoolSize ?? 1);
}

async function renderServerComposeTemplatePreview() {
    const form = $("#composeTemplateForm");
    if (!form) return;
    const requestId = ++composeTemplatePreviewRequestId;
    const blocks = collectComposeTemplateBlocksFromForm();
    const context = collectComposeTemplatePreviewContext();
    const strictPlaceholders = $("#previewComposeStrictPlaceholders")?.checked === true;
    const payload = {
        subject: form.subject.value || "",
        blocks,
        strictPlaceholders,
        contactId: context.contactId,
        orcidId: context.orcidId,
        expertEmail: context.expertEmail,
        senderAccountCode: context.senderAccountCode,
        variantIndex: state.previewDrawer.variantIndex
    };
    try {
        const result = await api("/api/compose-templates/preview-draft", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        if (requestId !== composeTemplatePreviewRequestId) return;
        if (!isPreviewDrawerOpen() || !isComposeTemplatePreviewTarget()) return;
        renderComposeTemplatePreviewInDrawer(result);
    } catch (_error) {
        if (requestId !== composeTemplatePreviewRequestId) return;
        const errorEl = $("#previewDrawerError");
        if (errorEl) {
            errorEl.hidden = false;
            errorEl.textContent = "预览失败，请重试";
        }
    }
}

async function randomComposeTemplatePreviewExpert() {
    const text = collectComposeTemplatePreviewSampleText();
    if (!text.trim()) {
        showStatus("请先输入主题或内容块再随机抽取", "error");
        return;
    }
    const result = await api("/api/qa/preview/random-expert", {
        method: "POST",
        body: JSON.stringify({ text, level: "CANDIDATE", mode: "SATISFY_ALL" })
    });
    if (result.error) {
        showStatus(`随机抽样暂不可用：${result.error}`, "error");
        return;
    }
    if (!result.expert) {
        showStatus("没有满足条件的专家", "error");
        return;
    }
    const label = result.expert.displayName && result.expert.email
        ? `${result.expert.displayName} <${result.expert.email}>`
        : (result.expert.displayName || result.expert.orcidId || "");
    const expertInput = $("#previewComposeExpertInput");
    if (expertInput) {
        expertInput.value = label;
    }
    state.previewDrawer.orcidId = result.expert.orcidId || null;
    state.previewDrawer.contactId = null;
    state.previewDrawer.expertEmail = result.expert.email || null;
    if (isPreviewDrawerOpen() && isComposeTemplatePreviewTarget()) {
        await renderServerComposeTemplatePreview();
    }
}

function renderComposeTemplatePreviewHtml(preview) {
    const blockNotes = (preview.blocks || []).map((block) => {
        const label = block.refDisplayName || composeBlockTypeLabels[block.blockType] || block.blockType;
        if (!block.included) {
            return `<div class="compose-block-pill skipped">#${block.blockOrder + 1} ${escapeHtml(label)} — 已跳过${block.skipReason ? `（${escapeHtml(block.skipReason)}）` : ""}</div>`;
        }
        return `<div class="compose-block-pill">#${block.blockOrder + 1} ${escapeHtml(label)}</div>`;
    }).join("");
    return `
        <div class="compose-preview-mail-head">
            <div><span>Subject</span><strong>${escapeHtml(preview.subject || "")}</strong></div>
        </div>
        <div class="compose-preview-block-notes">${blockNotes}</div>
        <div class="compose-preview-mail-body">${escapeHtml(preview.body || "")}</div>`;
}

async function saveComposeTemplate(event) {
    event.preventDefault();
    const form = $("#composeTemplateForm");
    const blocks = collectComposeTemplateBlocksFromForm();
    if (!blocks.length) {
        showStatus("请至少添加一个内容块", "error");
        return;
    }
    const payload = {
        templateName: form.templateName.value.trim(),
        subject: form.subject.value.trim(),
        description: form.description.value.trim() || null,
        enabled: form.enabled.checked,
        blocks
    };
    if (state.selectedComposeTemplateId) {
        await api(`/api/compose-templates/${state.selectedComposeTemplateId}`, {
            method: "PUT",
            body: JSON.stringify(payload)
        });
    } else {
        await api("/api/compose-templates", {
            method: "POST",
            body: JSON.stringify(payload)
        });
    }
    hideComposeTemplateEditor();
    state.mailSendOptions = [];
    await loadComposeTemplates();
    showStatus("邮件模板已保存", "ok");
}

async function handleComposeTemplateAction(button) {
    const action = button.dataset.action;
    const id = Number(button.dataset.id);
    const template = state.composeTemplates.find((item) => Number(item.id) === id);
    if (action === "edit-compose-template") {
        openComposeTemplateEditor(template);
    }
    if (action === "preview-compose-template") {
        openComposeTemplateEditor(template);
        openComposeTemplatePreview().catch((error) => showStatus(error.message, "error"));
    }
    if (action === "toggle-compose-template") {
        const enabled = button.dataset.enabled === "true";
        await api(`/api/compose-templates/${id}/${enabled ? "disable" : "enable"}`, { method: "POST" });
        state.mailSendOptions = [];
        await loadComposeTemplates();
    }
    if (action === "delete-compose-template") {
        if (!confirm("确定删除该邮件模板？")) return;
        await api(`/api/compose-templates/${id}`, { method: "DELETE" });
        state.mailSendOptions = [];
        await loadComposeTemplates();
    }
}

function handleComposeTemplateBlocksListClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    const blocks = collectComposeTemplateBlocksFromForm();
    const index = Number(button.dataset.index);
    if (action === "remove-compose-block") {
        blocks.splice(index, 1);
        renderComposeTemplateBlockRows(blocks);
        schedulePreviewDrawerRefresh();
        return;
    }
    if (action === "move-compose-block-up" && index > 0) {
        [blocks[index - 1], blocks[index]] = [blocks[index], blocks[index - 1]];
        renderComposeTemplateBlockRows(blocks);
        schedulePreviewDrawerRefresh();
        return;
    }
    if (action === "move-compose-block-down" && index < blocks.length - 1) {
        [blocks[index + 1], blocks[index]] = [blocks[index], blocks[index + 1]];
        renderComposeTemplateBlockRows(blocks);
        schedulePreviewDrawerRefresh();
    }
}

function handleComposeTemplateBlockTypeChange(event) {
    const select = event.target.closest(".block-type-select");
    if (!select) {
        schedulePreviewDrawerRefresh();
        return;
    }
    const row = select.closest(".compose-template-block-row");
    if (!row) return;
    const index = Number(row.dataset.blockIndex);
    const blocks = collectComposeTemplateBlocksFromForm();
    blocks[index] = {
        blockOrder: index,
        blockType: select.value,
        refId: null,
        customText: ""
    };
    renderComposeTemplateBlockRows(blocks);
    schedulePreviewDrawerRefresh();
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
            await showExpertDetail(expert);
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
    if (action === "expert-add-tag-open") {
        const editor = element.closest(".expert-tag-editor") || $("#expertTagEditor");
        if (!editor) return;
        const orcidId = editor.dataset.orcid;
        const level = editor.dataset.level;
        const editorId = editor.id || "expertTagEditor";
        const existingTags = await fetchExpertTagsFromEs(orcidId, level);
        const tag = await openExpertTagAddDialog(existingTags);
        if (!tag) return;
        if (existingTags.includes(tag)) {
            showStatus("标签已存在", "warn");
            return;
        }
        setTagEditorLoading(editor, true, "正在添加标签...");
        try {
            const tags = await mutateExpertTag(orcidId, level, tag, "add");
            updateExpertTagEditor(orcidId, tags, level, editorId);
            showStatus("标签已添加", "ok");
        } catch (e) {
            setTagEditorLoading(editor, false);
            showStatus(e.message, "error");
        }
        return;
    }
    if (action === "expert-remove-tag") {
        const editor = element.closest(".expert-tag-editor") || $("#expertTagEditor");
        if (!editor) return;
        const orcidId = editor.dataset.orcid;
        const level = editor.dataset.level;
        const editorId = editor.id || "expertTagEditor";
        const tag = element.dataset.tag;
        if (!tag) return;
        setTagEditorLoading(editor, true, "正在删除标签...");
        try {
            const tags = await mutateExpertTag(orcidId, level, tag, "remove");
            updateExpertTagEditor(orcidId, tags, level, editorId);
            showStatus("标签已删除", "ok");
        } catch (e) {
            setTagEditorLoading(editor, false);
            showStatus(e.message, "error");
        }
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
    if (action === "preview-document") {
        const previewUrl = element.dataset.url;
        if (previewUrl) window.open(`${contextPath}${previewUrl}`, "_blank");
        return;
    }
    if (action === "open-ai-analysis") {
        const contactId = Number(element.dataset.contactId);
        if (contactId) await openAiAnalysisModal(contactId);
        return;
    }
    if (action === "close-ai-analysis") {
        closeAiAnalysisModal();
        return;
    }
    if (action === "start-ai-analysis") {
        await startAiAnalysis();
        return;
    }
    if (action === "ai-analysis-reanalyze") {
        aiAnalysisState.mode = "select";
        aiAnalysisState.error = null;
        renderAiAnalysisModal();
        return;
    }
    if (action === "ai-analysis-add-field") {
        await addAiAnalysisField();
        return;
    }
}

async function handleOperatorStatusChange(contactId, newStatus) {
    const operatorName = window.localStorage.getItem("operatorName") || "console";
    if (!newStatus) return;
    await api(`/api/expert-contacts/${contactId}/operator-status`, {
        method: "POST",
        body: JSON.stringify({ operatorStatus: newStatus, operatorName })
    });
}

async function handleIndexLevelChange(contactId, newLevel) {
    const operatorName = window.localStorage.getItem("operatorName") || "console";
    if (!newLevel) return;
    await api(`/api/expert-contacts/${contactId}/index-level`, {
        method: "POST",
        body: JSON.stringify({ targetLevel: newLevel, operatorName })
    });
}

function updateSaveButtonState() {
    const saveBtn = $("#saveContactChangesBtn");
    if (!saveBtn) return;

    const statusSelect = $("#operatorStatusSelect");
    const levelSelect = $("#indexLevelSelect");
    const replySelect = $("#autoReplySelect");

    const hasChanges =
        (statusSelect && statusSelect.value !== statusSelect.dataset.original) ||
        (levelSelect && levelSelect.value !== levelSelect.dataset.original) ||
        (replySelect && replySelect.value !== replySelect.dataset.original);

    saveBtn.disabled = !hasChanges;

    [statusSelect, levelSelect, replySelect].forEach(sel => {
        if (!sel) return;
        if (sel.value !== sel.dataset.original) {
            sel.style.borderColor = "var(--warning)";
            sel.style.boxShadow = "0 0 0 1px var(--warning)";
        } else {
            sel.style.borderColor = "";
            sel.style.boxShadow = "";
        }
    });
}

function normalizeDiscoveryResultSummary(resultSummary) {
    if (!resultSummary) return null;
    var summary;
    try {
        summary = typeof resultSummary === "string"
            ? JSON.parse(resultSummary)
            : resultSummary;
    } catch (e) {
        return null;
    }
    if (!summary) return null;
    if (!summary.stats) return summary;
    return {
        ...summary.stats,
        summaryText: summary.summaryText ?? summary.stats.summaryText
    };
}

function renderDiscoverySummaryText(summaryText) {
    if (!summaryText) return "";
    return `<div style="margin-top:6px;font-size:11px;color:var(--text-muted);">${escapeHtml(summaryText)}</div>`;
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
        <tr class="task-row" data-task-id="${task.id}" data-task-type="${escapeHtml(task.taskType)}" onclick="toggleTaskDetail(this)" style="cursor:pointer;">
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

async function toggleTaskDetail(row) {
    const existingDetail = row.nextElementSibling;
    if (existingDetail?.classList.contains("task-detail-row")) {
        existingDetail.remove();
        return;
    }
    const taskId = row.dataset.taskId;
    const taskType = row.dataset.taskType;
    let data;
    if (taskType === "EXPERT_DISCOVERY") {
        try {
            const task = await api(`/api/task-executions/${taskId}`);
            if (task && task.resultSummary) {
                data = normalizeDiscoveryResultSummary(task.resultSummary);
            }
        } catch (e) { data = null; }
    } else {
        try {
            data = await api(`/api/task-executions/recent-polls/${taskId}/detail`);
        } catch (e) { data = null; }
    }
    let bySourceHtml = "";
    if (data && data.bySource) {
        const container = document.createElement("div");
        renderBySourceTable(data.bySource, container);
        bySourceHtml = container.innerHTML;
    }
    if (data && data.summaryText) {
        bySourceHtml += renderDiscoverySummaryText(data.summaryText);
    }
    const detailRow = document.createElement("tr");
    detailRow.className = "task-detail-row";
    detailRow.innerHTML = `<td colspan="7" style="padding:12px 16px;background:var(--surface);">${bySourceHtml || '<div class="text-muted">暂无明细</div>'}</td>`;
    row.after(detailRow);
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

async function refreshUnmatchedBadge() {
    try {
        const data = await api("/api/mail/unmatched-inbound?pageSize=1&pageOffset=0");
        updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
    } catch (_) {
    }
}

function updateUnmatchedBadge(counts, total) {
    if (!counts) {
        api("/api/mail/unmatched-inbound?pageSize=1&pageOffset=0").then(data => {
            updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
        }).catch(() => {});
        return;
    }
    const high = Array.from(HIGH_PRIORITY_REASON_TYPES).reduce((s, k) => s + (counts[k] || 0), 0);
    const t = typeof total === "number" ? total : Object.values(counts).reduce((s, v) => s + (v || 0), 0);
    const normal = Math.max(0, t - high);
    setBadge("#unmatchedBadgeHigh", high);
    setBadge("#unmatchedBadgeNormal", normal);
}

function setBadge(sel, n) {
    const el = $(sel);
    if (!el) return;
    if (n > 0) { el.textContent = n > 99 ? "99+" : n; el.hidden = false; }
    else el.hidden = true;
}

const MAILBOX_TAG_BADGE_CLASS = {
    "专家": "ok",
    "待匹配": "warn",
    "自动回复": "warn",
    "手动回复": "",
    "首发": "",
    "待处理": "error",
    "收件": "",
    "发件": "ok"
};

function renderMailboxTagBadges(tags) {
    return (tags || []).map((tag) => badge(tag, MAILBOX_TAG_BADGE_CLASS[tag] || "")).join(" ");
}

function renderMailboxActions(row) {
    const actions = [];
    const canProcess = row.source === "INBOUND_PROCESSING"
        && row.processStatus === "MANUAL_REVIEW"
        && row.inboundProcessingId;

    if (canProcess) {
        // 可处理：查看/处理 打开处理面板（含查看），并额外提供快捷处理
        actions.push(`<button class="button primary" data-action="open-pending" data-id="${row.inboundProcessingId}">查看/处理</button>`);
        actions.push(`<button class="button" data-action="mark-unmatched-resolved" data-id="${row.inboundProcessingId}">标记已处理</button>`);
    } else {
        // 不可处理：单纯查看原文；关联专家时保留跳转
        if (row.expertContactId) {
            actions.push(`<button class="button" data-action="open-monitoring-contact" data-id="${row.expertContactId}">查看专家</button>`);
        }
        actions.push(`<button class="button" data-action="view-mail" data-source="${escapeHtml(row.source || "")}" data-id="${escapeHtml(row.id)}">查看</button>`);
    }
    return actions.join(" ") || "-";
}

async function showMailDetail(source, id) {
    try {
        const detail = await api(`/api/mail/mailbox/${encodeURIComponent(source)}/${id}`);

        const timeStr = detail.timestamp ? detail.timestamp.replace("T", " ").slice(0, 19) : "-";
        const directionLabel = labelMailDirection(detail.direction);
        const mailTypeLabel = labelMailType(detail.mailType);
        const attachmentLabel = detail.hasAttachment ? "有附件" : "无附件";
        const sendStatusLabel = detail.direction === "OUTBOUND"
            ? (detail.sendStatus === "SENT" ? "已发送" : detail.sendStatus === "FAILED" ? "发送失败" : detail.sendStatus || "-")
            : "-";
        const body = detail.body || "";

        let attachmentSectionHtml = "";
        if (detail.hasAttachment) {
            try {
                const attachments = await api(
                    `/api/mail/mailbox/${encodeURIComponent(source)}/${id}/attachments`
                );
                attachmentSectionHtml = renderMailboxAttachments(attachments);
            } catch (e) {
                attachmentSectionHtml = `<p style="color: var(--text-muted); font-size: 12px;">附件加载失败：${escapeHtml(e.message)}</p>`;
            }
        }

        // 复用工单详情面板（内联，不弹框），仅展示，不含任何处理功能
        const panel = $("#unmatchedDetailPanel");
        panel.hidden = false;
        const inboundProcessingId = detail.inboundProcessingId || null;
        state.mailbox.detailContext = inboundProcessingId
            ? { source, id: Number(id), inboundProcessingId }
            : null;
        const tagSectionHtml = inboundProcessingId
            ? renderMailboxInboundTagEditor(detail.inboundTags || [], inboundProcessingId)
            : "";
        const expertOrcidId = detail.expertOrcidId || "";
        const expertIndexLevel = detail.expertIndexLevel || "CANDIDATE";
        const expertTagSectionHtml = expertOrcidId
            ? renderMailboxExpertTagEditor(
                detail,
                await fetchExpertTagsFromEs(expertOrcidId, expertIndexLevel),
                "mailboxExpertTagEditor"
            )
            : "";
        panel.innerHTML = `
            <div class="panel-head">
                <h2>邮件详情</h2>
                <button class="button secondary" data-action="close-unmatched-detail">关闭</button>
            </div>
            <div class="unmatched-detail-body">
                <div class="metadata-grid">
                    <div class="metadata-card"><div class="metadata-card-header"><span>时间</span></div><div class="metadata-card-value">${escapeHtml(timeStr)}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>方向</span></div><div class="metadata-card-value">${escapeHtml(directionLabel)}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>邮件类型</span></div><div class="metadata-card-value">${escapeHtml(mailTypeLabel)}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>邮箱账号</span></div><div class="metadata-card-value">${escapeHtml(detail.senderAccountCode || "-")}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>专家邮箱</span></div><div class="metadata-card-value">${escapeHtml(detail.expertEmail || "-")}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>专家姓名</span></div><div class="metadata-card-value">${escapeHtml(detail.expertName || "-")}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>附件</span></div><div class="metadata-card-value">${escapeHtml(attachmentLabel)}</div></div>
                    <div class="metadata-card"><div class="metadata-card-header"><span>发送状态</span></div><div class="metadata-card-value">${escapeHtml(sendStatusLabel)}</div></div>
                    <div class="metadata-card" style="grid-column: 1 / -1;"><div class="metadata-card-header"><span>主题</span></div><div class="metadata-card-value">${escapeHtml(detail.subject || "-")}</div></div>
                </div>
                ${tagSectionHtml}
                ${expertTagSectionHtml}
                ${detail.hasAttachment ? `
                <div class="detail-section">
                    <h3>附件</h3>
                    ${attachmentSectionHtml}
                </div>` : ""}
                <div class="detail-section">
                    <h3>正文</h3>
                    ${translatableBody(body, { emptyLabel: "无正文" })}
                </div>
            </div>
        `;
        focusMailboxProcessingPanel();
    } catch (e) {
        showStatus(e.message, "error");
    }
}

function renderMailboxAttachments(attachments) {
    const list = Array.isArray(attachments) ? attachments : [];
    if (list.length === 0) {
        return `<p style="color: var(--text-muted); font-size: 12px;">暂无附件。</p>`;
    }
    return `
        <div class="document-list">
            ${list.map(item => `
                <div class="document-row">
                    <div>
                        <strong>${escapeHtml(item.fileName || "?")}</strong>
                        <span>${escapeHtml(item.contentType || "-")}&nbsp;·&nbsp;${formatFileSize(item.fileSize)}</span>
                    </div>
                    <div class="document-actions">
                        <a class="button small" href="${contextPath}/api/mail/mailbox/attachments/${encodeURIComponent(item.id)}/download" download>下载</a>
                    </div>
                </div>
            `).join("")}
        </div>
    `;
}

async function refreshMailboxAfterPendingAction() {
    await loadMailbox();
    await refreshUnmatchedBadge();
}

function findSuggestRule(suggest, ruleId) {
    for (const category of suggest.rulesByCategory || []) {
        const rule = category.rules.find((item) => item.id === ruleId);
        if (rule) {
            return { ...rule, categoryId: category.categoryId, composeOrder: category.composeOrder };
        }
    }
    const suggested = (suggest.suggestedRules || []).find((item) => item.id === ruleId);
    return suggested || null;
}

function requestCoverageBadgeClass(status) {
    switch (status) {
        case "GROUNDED":
            return "ok";
        case "NEEDS_REVIEW":
        case "PARTIAL":
            return "warn";
        case "UNSUPPORTED":
        case "BLOCKED":
            return "error";
        default:
            return "";
    }
}

function requestCoverageBadgeLabel(status) {
    switch (status) {
        case "GROUNDED":
            return "完整";
        case "NEEDS_REVIEW":
            return "需复核";
        case "PARTIAL":
            return "部分";
        case "UNSUPPORTED":
            return "缺依据";
        case "BLOCKED":
            return "阻断";
        default:
            return status || "未知";
    }
}

function activeComposedRequestCoverage() {
    return composedReplyState.evaluation?.requestCoverage
        || composedReplyState.suggest?.requestCoverage
        || [];
}

function clearComposedDraftSession() {
    composedReplyState.draft = null;
    composedReplyState.lockedFactIds = null;
    if (composedReplyState.recordId != null) {
        resetAiReplyState(composedReplyState.recordId);
    }
    renderComposedDraftPreview();
    updateTrustWorkbenchButtons();
}

function syncComposeFactCheckboxes() {
    const selected = new Set(composedReplyState.selectedFactIds);
    document.querySelectorAll(".compose-rule-checkbox").forEach((checkbox) => {
        checkbox.checked = selected.has(Number(checkbox.dataset.ruleId));
    });
}

function debouncedEvaluateComposedFacts() {
    if (composedReplyState.evaluateTimer) {
        clearTimeout(composedReplyState.evaluateTimer);
    }
    composedReplyState.evaluateTimer = setTimeout(() => {
        composedReplyState.evaluateTimer = null;
        evaluateComposedFacts().catch((error) => showStatus(error.message, "error"));
    }, 300);
}

async function evaluateComposedFacts() {
    const recordId = composedReplyState.recordId;
    if (!recordId) return;
    const seq = ++composedReplyState.evaluateSeq;
    const requestedFactIds = [...composedReplyState.selectedFactIds];
    composedReplyState.evaluationPending = true;
    composedReplyState.confirmedEvaluation = null;
    try {
        const evaluation = await api(`/api/mail/unmatched-inbound/${recordId}/composed-reply/evaluate`, {
            method: "POST",
            body: JSON.stringify({ factRuleIds: requestedFactIds })
        });
        if (seq !== composedReplyState.evaluateSeq) return;
        if (!sameFactIdSet(requestedFactIds, composedReplyState.selectedFactIds)) return;
        composedReplyState.evaluation = evaluation;
        composedReplyState.confirmedEvaluation = evaluation;
        composedReplyState.evaluationPending = false;
        composedReplyState.selectedFactIds = [...(evaluation.canonicalFactIds || [])];
        syncComposeFactCheckboxes();
        refreshComposedWorkbenchUI();
    } catch (error) {
        if (seq === composedReplyState.evaluateSeq) {
            composedReplyState.evaluationPending = false;
            refreshComposedWorkbenchUI();
        }
        throw error;
    }
}

function renderComposedGapList() {
    const list = $("#composedGapList");
    if (!list || !composedReplyState.suggest) return;
    const requestCoverage = activeComposedRequestCoverage();
    const countEl = $("#composedGapCount");
    if (!requestCoverage.length) {
        list.innerHTML = `<li class="text-muted">暂无问题项</li>`;
        if (countEl) countEl.textContent = "";
        return;
    }
    const evidenceSources = composedReplyState.draft?.result?.evidenceSources || [];
    const evidenceById = {};
    evidenceSources.forEach(es => {
        if (es && es.ruleId) evidenceById[es.ruleId] = es;
    });
    const groundedCount = requestCoverage.filter((item) => item.status === "GROUNDED").length;
    if (countEl) countEl.textContent = `${groundedCount}/${requestCoverage.length}`;
    list.innerHTML = requestCoverage.map((item) => {
        const status = String(item.status || "");
        const factRuleIds = item.factRuleIds || [];
        const symbol = status === "GROUNDED" ? "✓" : "○";
        const hasFacts = factRuleIds.length > 0;
        let factHint = "";
        if (hasFacts) {
            const names = factRuleIds.map((ruleId) => {
                return escapeHtml(resolveFactDisplayName(ruleId, evidenceSources, composedReplyState.suggest));
            });
            factHint = `<span class="gap-no-rules-hint">依据：${names.join("；")}</span>`;
        }
        const noRuleHint = hasFacts ? factHint : `<span class="gap-no-rules-hint">暂无可核验事实</span>`;
        const statusBadge = badge(
            requestCoverageBadgeLabel(status),
            requestCoverageBadgeClass(status)
        );
        return `
        <li>
            <span>${symbol}</span>
            <span>${escapeHtml(item.requestText || "")}${noRuleHint}</span>
            ${statusBadge}
        </li>`;
    }).join("");
}

function renderComposedSelectedList() {
    const list = $("#composedSelectedList");
    if (!list || !composedReplyState.suggest) return;
    const factIds = composedReplyState.evaluationPending
        ? composedReplyState.selectedFactIds
        : (confirmedCanonicalFactIds() || composedReplyState.selectedFactIds);
    list.innerHTML = factIds.map((ruleId) => {
        const rule = findSuggestRule(composedReplyState.suggest, ruleId);
        const label = resolveFactDisplayName(ruleId, composedReplyState.draft?.result?.evidenceSources, composedReplyState.suggest);
        return `<li data-rule-id="${ruleId}"><span>${escapeHtml(label)}</span></li>`;
    }).join("") || `<li class="text-muted">未选择事实</li>`;
}

function renderComposedDraftPreview() {
    const container = $("#composedRenderedPreview");
    const feedback = $("#trustReplyFeedback");
    if (!container) return;
    const draft = composedReplyState.draft;
    if (!draft?.rendered) {
        container.textContent = "";
        if (feedback) {
            feedback.hidden = true;
            feedback.innerHTML = "";
        }
        return;
    }
    container.textContent = draft.rendered;
    renderAiReplyFeedback(feedback, draft.result || null);
}

function updateTrustWorkbenchButtons() {
    const generateBtn = $("#trustGenerateDraftBtn");
    const adoptBtn = $("#trustAdoptDraftBtn");
    const heading = document.getElementById("trustDraftHeading");
    const hasConfirmedFacts = (confirmedCanonicalFactIds() || []).length > 0;
    const canGenerateContinuation = aiReplyState.firstTurnDone && !!composedReplyState.lockedFactIds?.length;
    const draftResult = composedReplyState.draft?.result;
    const generationFailed = !!composedReplyState.draft?.rendered
        && !isAiReplyGenerationSuccess(draftResult);
    const failureCode = generationFailed ? resolveAiReplyFailureReasonFromResult(draftResult) : null;

    if (generateBtn) {
        generateBtn.textContent = aiReplyState.firstTurnDone ? "重新生成表达" : "生成可信草稿";
        generateBtn.disabled = composedReplyState.suggest?.llmEnabled === false
            || aiReplyState.inFlight
            || composedReplyState.evaluationPending
            || (!canGenerateContinuation && !hasConfirmedFacts);
        if (generationFailed) {
            generateBtn.textContent = "重试生成";
        }
    }
    if (adoptBtn) {
        if (generationFailed) {
            adoptBtn.disabled = true;
            adoptBtn.setAttribute("aria-disabled", "true");
            adoptBtn.title = "LLM 生成失败，当前 QA 规则参考内容不可采用";
        } else {
            adoptBtn.disabled = !composedReplyState.draft?.rendered
                || composedReplyState.evaluationPending
                || !(composedReplyState.lockedFactIds?.length || hasConfirmedFacts);
            adoptBtn.removeAttribute("aria-disabled");
            adoptBtn.removeAttribute("title");
        }
    }
    if (heading) {
        heading.textContent = generationFailed ? "QA 规则参考内容" : "可信草稿";
    }
}

function refreshComposedWorkbenchUI() {
    renderComposedSelectedList();
    renderComposedGapList();
    renderComposedDraftPreview();
    updateTrustWorkbenchButtons();
}

function sortCategoryRulesForDisplay(rules, suggestedSet) {
    const suggested = [];
    const others = [];
    rules.forEach((rule) => {
        if (suggestedSet.has(rule.id)) {
            suggested.push(rule);
        } else {
            others.push(rule);
        }
    });
    return [...suggested, ...others];
}

function initComposedReplyWorkbench(recordId, suggest) {
    manualReplyQaContext = null;
    composedReplyState.recordId = recordId;
    composedReplyState.suggest = suggest;
    composedReplyState.selectedFactIds = [...(suggest.suggestedRuleIds || [])];
    const initialEvaluation = {
        canonicalFactIds: [...(suggest.suggestedRuleIds || [])],
        suggestedFactIds: [...(suggest.suggestedRuleIds || [])],
        draftReadiness: suggest.draftReadiness || "READY",
        requestCoverage: suggest.requestCoverage || [],
        gapDetected: !!suggest.gapDetected
    };
    composedReplyState.evaluation = initialEvaluation;
    composedReplyState.confirmedEvaluation = initialEvaluation;
    composedReplyState.evaluationPending = false;
    composedReplyState.lockedFactIds = null;
    composedReplyState.draft = null;
    composedReplyState.evaluateSeq = 0;
    resetAiReplyState(recordId);

    document.querySelectorAll(".compose-rule-checkbox").forEach((checkbox) => {
        checkbox.addEventListener("change", () => {
            const ruleId = Number(checkbox.dataset.ruleId);
            if (checkbox.checked) {
                if (!composedReplyState.selectedFactIds.includes(ruleId)) {
                    composedReplyState.selectedFactIds.push(ruleId);
                }
            } else {
                composedReplyState.selectedFactIds = composedReplyState.selectedFactIds.filter((id) => id !== ruleId);
            }
            markComposedEvaluationPending();
            debouncedEvaluateComposedFacts();
        });
    });

    const modelSelect = $("#trustReplyModel");
    if (modelSelect) {
        modelSelect.value = aiReplyState.selectedModel || "DEEPSEEK_V4_FLASH";
    }

    refreshComposedWorkbenchUI();
    evaluateComposedFacts().catch((error) => showStatus(error.message, "error"));
}

const autoReplyPreviewKindLabels = {
    QA_AUTO_REPLIED: { text: "QA 自动回复", badge: "ok" },
    QA_NO_MATCH: { text: "QA 未命中", badge: "warn" },
    QA_GAP: { text: "QA 缺口", badge: "warn-yellow" },
    MEETING_INVITATION: { text: "会议邀请", badge: "info" },
    MEETING_ALREADY_SENT: { text: "会议已发", badge: "warn-yellow" },
    MANUAL_HANDOFF: { text: "转人工", badge: "warn" }
};

const autoReplyBlockedLabels = {
    AUTO_REPLY_DISABLED: "自动回复已关闭",
    MANUAL_HANDOFF_STATUS: "当前为人工接管状态",
    INTRODUCTION_NOT_SENT: "尚未发送介绍信",
    RECIPIENT_UNSUBSCRIBED: "收件人已退订"
};

function renderAutoReplyPreviewHtml(preview) {
    const kindMeta = autoReplyPreviewKindLabels[preview.previewKind] || {
        text: preview.previewKind,
        badge: ""
    };
    const intentLine = `
        <p class="text-muted" style="margin:8px 0;">
            意图：<strong>${escapeHtml(preview.intentCode)}</strong>
            · 置信度 ${preview.confidence}
            ${preview.matchedKeywords?.length
                ? ` · 关键词：${escapeHtml(preview.matchedKeywords.join(", "))}`
                : ""}
        </p>`;

    const blockedHtml = (preview.wouldBeBlockedBy || []).length > 0 ? `
        <div class="auto-reply-preview-notice">
            当前若收到此信不会自动发送，原因：${escapeHtml(
                preview.wouldBeBlockedBy.map((code) => autoReplyBlockedLabels[code] || code).join("；")
            )}（本预览仍展示假如开启会回的内容）
        </div>` : "";

    const attachmentHtml = preview.attachmentIntentIgnored ? `
        <div class="auto-reply-preview-notice">
            该来信含附件，本预览未计入附件意图推断，实际自动处理可能转人工
        </div>` : "";

    const replyHtml = preview.replyBody ? `
        <div style="margin-top:8px;">
            <h4 style="margin-bottom:6px;">${escapeHtml(preview.replySubject || "（无主题）")}</h4>
            ${translatableBody(preview.replyBody)}
        </div>` : "";

    const reasonHtml = preview.reason && !preview.replyBody ? `
        <p style="margin:8px 0;"><strong>转人工原因：</strong>${escapeHtml(preview.reason)}</p>` : "";

    const rulesHtml = preview.matchedRuleIds?.length
        ? `<p class="text-muted" style="font-size:12px;margin:4px 0 0;">命中规则 ID：${escapeHtml(preview.matchedRuleIds.join(", "))}</p>`
        : "";

    return `
        <div class="auto-reply-preview-result">
            ${badge(kindMeta.text, kindMeta.badge)}
            ${intentLine}
            ${blockedHtml}
            ${attachmentHtml}
            ${reasonHtml}
            ${replyHtml}
            ${rulesHtml}
        </div>`;
}

function renderAutoReplyPreviewSummary(preview) {
    const kindMeta = autoReplyPreviewKindLabels[preview.previewKind] || {
        text: preview.previewKind || "预览完成",
        badge: ""
    };
    const blockedCount = preview.wouldBeBlockedBy?.length || 0;
    const ruleCount = preview.matchedRuleIds?.length || 0;
    return {
        status: blockedCount > 0 ? `有 ${blockedCount} 项阻断` : kindMeta.text,
        meta: [
            preview.intentCode ? `意图：${preview.intentCode}` : "",
            preview.confidence != null ? `置信度 ${preview.confidence}` : "",
            ruleCount > 0 ? `命中 ${ruleCount} 条规则` : "未命中规则"
        ].filter(Boolean).join(" · ")
    };
}

async function loadAutoReplyPreview(recordId) {
    const resultEl = $("#autoReplyPreviewResult");
    const statusEl = $("#autoReplyPreviewStatus");
    const metaEl = $("#autoReplyPreviewMeta");
    if (resultEl) resultEl.innerHTML = `<p class="text-muted">加载预览中…</p>`;
    if (statusEl) statusEl.textContent = "生成中…";
    if (metaEl) metaEl.textContent = "正在分析来信意图与回复规则";
    try {
        const preview = await api(`/api/mail/unmatched-inbound/${recordId}/auto-reply-preview`);
        if (String(state.mailbox.detailContext?.id) !== String(recordId)) return null;
        const summary = renderAutoReplyPreviewSummary(preview);
        if (resultEl) resultEl.innerHTML = renderAutoReplyPreviewHtml(preview);
        if (statusEl) statusEl.textContent = summary.status;
        if (metaEl) metaEl.textContent = summary.meta;
        return preview;
    } catch (error) {
        if (resultEl) resultEl.innerHTML = `<p class="text-muted">${escapeHtml(error.message || "预览失败")}</p>`;
        if (statusEl) statusEl.textContent = "预览失败";
        if (metaEl) metaEl.textContent = error.message || "请稍后重试";
        throw error;
    }
}

function renderComposedReplyWorkbenchHtml(suggest, recordId) {
    const suggestedSet = new Set(suggest.suggestedRuleIds || []);
    const categoriesHtml = (suggest.rulesByCategory || []).map((category) => `
        <details class="compose-category-panel" open>
            <summary>${escapeHtml(category.categoryName)}</summary>
            <div class="compose-rule-list">
                ${sortCategoryRulesForDisplay(category.rules, suggestedSet).map((rule) => {
                    const checked = suggestedSet.has(rule.id) ? "checked" : "";
                    const suggested = suggestedSet.has(rule.id)
                        ? `<span class="badge ok">建议</span>` : "";
                    const label = (rule.displayName && rule.displayName.trim() && rule.displayName !== "未命名事实")
                        ? rule.displayName
                        : (rule.sectionTitle && rule.sectionTitle.trim() && rule.sectionTitle !== "未命名事实")
                            ? rule.sectionTitle
                            : (rule.replySubject && rule.replySubject.trim() && rule.replySubject !== "未命名事实")
                                ? rule.replySubject
                                : "事实名称缺失";
                    return `
                        <label class="compose-rule-item">
                            <input type="checkbox" class="compose-rule-checkbox" data-rule-id="${rule.id}" ${checked}>
                            <span>${escapeHtml(label)}</span>
                            ${suggested}
                        </label>`;
                }).join("")}
            </div>
        </details>
    `).join("");

    const llmDisabled = suggest.llmEnabled === false;

    return `
        <details class="detail-section reply-workflow-detail compose-workbench-section">
            <summary class="reply-workflow-summary">
                <span class="reply-workflow-icon" aria-hidden="true">⌘</span>
                <span class="reply-workflow-title"><strong>可信回复工作台</strong><small>选择事实、生成草稿、采用后人工发送</small></span>
                <span class="reply-workflow-status">已匹配 ${suggest.suggestedRuleIds?.length || 0} 条</span>
                <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
            </summary>
            <div class="reply-workflow-content">
            <div class="compose-workbench">
                <div class="compose-panel compose-fragments">
                    <h4>可用事实</h4>
                    ${categoriesHtml || "<p class='text-muted'>暂无可用事实</p>"}
                </div>
                <div class="compose-panel compose-draft ai-chat-panel">
                    <h4 id="trustDraftHeading">可信草稿</h4>
                    <div class="ai-reply-model-row ai-reply-generation-controls">
                        <label>生成模型
                            <select id="trustReplyModel" class="ai-reply-model-select"${llmDisabled ? " disabled" : ""}>
                                <option value="DEEPSEEK_V4_FLASH">DeepSeek V4 Flash</option>
                                <option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>
                            </select>
                        </label>
                        <label>单次 TTL
                            <select id="trustReplyAttemptTimeout" class="ai-reply-model-select ai-reply-timeout-select">
                                <option value="30">30 秒（默认）</option><option value="60">60 秒</option><option value="90">90 秒</option><option value="180">180 秒</option><option value="custom">自定义</option>
                            </select>
                        </label>
                        <label id="trustReplyAttemptTimeoutCustomWrap" class="ai-reply-timeout-custom-wrap" hidden>
                            <input id="trustReplyAttemptTimeoutCustom" class="ai-reply-timeout-custom-input" type="number" min="10" max="600" step="1" value="30" aria-label="自定义单次生成超时秒数"><span>秒</span>
                        </label>
                        <label>总 TTL
                            <select id="trustReplyTotalTimeout" class="ai-reply-model-select ai-reply-timeout-select">
                                <option value="auto">自动（300 秒）</option><option value="300">300 秒</option><option value="600">600 秒</option><option value="900">900 秒</option><option value="1800">1800 秒</option><option value="custom">自定义</option>
                            </select>
                        </label>
                        <label id="trustReplyTotalTimeoutCustomWrap" class="ai-reply-timeout-custom-wrap" hidden>
                            <input id="trustReplyTotalTimeoutCustom" class="ai-reply-timeout-custom-input" type="number" min="10" max="7200" step="1" value="300" aria-label="自定义生成总超时秒数"><span>秒</span>
                        </label>
                    </div>
                    <div id="trustReplyFeedback" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
                    <ul id="composedSelectedList" class="compose-selected-list"></ul>
                    <textarea id="composedOperatorInstruction" class="compose-free-text" placeholder="可选：语气、长度、结构要求（不会作为事实写入正文）"${llmDisabled ? " disabled" : ""}></textarea>
                    <div id="composedRenderedPreview" class="compose-rendered-preview pre"></div>
                    <div class="compose-draft-actions">
                        <button type="button" class="button primary" id="trustGenerateDraftBtn" data-action="trust-generate-draft" data-record-id="${recordId}"${llmDisabled ? " disabled" : ""}>生成可信草稿</button>
                        <button type="button" class="button secondary" id="trustAdoptDraftBtn" data-action="trust-adopt-draft" data-record-id="${recordId}" disabled>采用到人工回复</button>
                    </div>
                </div>
                <div class="compose-panel compose-gaps">
                    <h4>问题与依据<span class="compose-count" id="composedGapCount"></span></h4>
                    <ul id="composedGapList" class="compose-gap-list"></ul>
                </div>
            </div>
            </div>
        </details>`;
}

function resetAiReplyState(recordId) {
    if (aiReplyState.activeGeneration) void cancelActiveAiReplyGeneration();
    stopAiReplyProgressTicker();
    aiReplyState.requestSeq += 1;
    aiReplyState.recordId = recordId;
    aiReplyState.turns = [];
    aiReplyState.lastDraftTemplate = "";
    aiReplyState.lastRenderedDraft = "";
    aiReplyState.lastQaRuleIds = [];
    aiReplyState.mode = null;
    aiReplyState.firstTurnDone = false;
    aiReplyState.drafts = {};
    aiReplyState.nextDraftId = 0;
    aiReplyState.adoptContext = null;
    aiReplyState.inFlight = false;
    aiReplyState.activeGeneration = null;
    aiReplyState.latestProgress = null;
    aiReplyState.lastProgressSeq = -1;
    aiReplyState.progressReceivedAt = 0;
    resetPreflightState();
}

function appendAiChatOperatorBubble(instruction) {
    const container = $("#aiChatMessages");
    if (!container) return;
    const bubble = document.createElement("div");
    bubble.className = "ai-chat-bubble ai-chat-operator";
    bubble.innerHTML = `<div class="ai-chat-label">修改要求</div><div class="pre">${escapeHtml(instruction)}</div>`;
    container.appendChild(bubble);
    container.scrollTop = container.scrollHeight;
}

function appendAiChatDraftBubble(rawText, renderedText, result) {
    const container = $("#aiChatMessages");
    if (!container) return;
    const draftId = ++aiReplyState.nextDraftId;
    const rendered = renderedText || rawText || "";
    const requestCoverage = (result && result.requestCoverage) || [];
    const coverageSummary = summarizeAiReplyCoverage(requestCoverage);
    const readiness = resolveAiDraftReadiness(result, coverageSummary);
    const generationFailed = !isAiReplyGenerationSuccess(result);
    let label = "AI 草稿";
    if (generationFailed) {
        label = "QA 规则参考（AI 未生成）";
    } else if (readiness === "NEEDS_REVIEW") {
        label = "AI 草稿 — 需补充";
    } else if (readiness === "BLOCKED") {
        label = "AI 草稿 — 缺依据";
    }
    let adoptLabel = "采用此草稿";
    const adoptDisabledAttr = generationFailed ? " disabled title=\"LLM 生成失败，当前 QA 规则参考内容不可采用\"" : "";
    aiReplyState.drafts[draftId] = {
        raw: rawText || "",
        rendered,
        needsGroundingReview: coverageSummary.needsGroundingReview,
        reviewItems: coverageSummary.reviewItems,
        draftReadiness: readiness,
        requestCount: Number((result && result.requestCount)) || 0,
        mode: (result && result.mode) || "",
        qaRuleIds: (result && result.qaRuleIds) || [],
        requestCoverage: requestCoverage,
        evidenceSources: (result && result.evidenceSources) || [],
        evidenceSetVersion: (result && result.evidenceSetVersion) || "",
        promptVersion: (result && result.promptVersion) || "",
        draftHash: (result && result.draftHash) || "",
        usedLlm: (result && result.usedLlm) === true,
        generationState: (result && result.generationState) || ""
    };
    const bubble = document.createElement("div");
    bubble.className = "ai-chat-bubble ai-chat-assistant";
    bubble.innerHTML = `
        <div class="ai-chat-label">${escapeHtml(label)}</div>
        ${translatableBody(rendered)}
        <div class="ai-chat-draft-actions">
            <button type="button" class="button small primary" data-action="ai-adopt-draft" data-draft-id="${draftId}"${adoptDisabledAttr}>${escapeHtml(adoptLabel)}</button>
        </div>`;
    container.appendChild(bubble);
    container.scrollTop = container.scrollHeight;
}

function initAiReplyWorkbench(recordId) {
    resetAiReplyState(recordId);
    const container = $("#aiChatMessages");
    if (container) container.innerHTML = "";
    const modelSelect = $("#aiMailboxReplyModel");
    if (modelSelect) {
        modelSelect.value = aiReplyState.selectedModel || "DEEPSEEK_V4_FLASH";
    }
    syncAiReplyTimeoutControls();
}

function schedulePreflightCheck() {
    const adopt = aiReplyState.adoptContext;
    if (!adopt || Number(adopt.recordId) !== Number(state.mailbox.detailContext?.id)) {
        return;
    }
    if (preflightState.timerId) {
        clearTimeout(preflightState.timerId);
    }
    const capturedDraftId = adopt.draftId;
    preflightState.timerId = setTimeout(() => {
        preflightState.timerId = null;
        doPreflightCheck(Number(adopt.recordId), capturedDraftId);
    }, 500);
}

async function doPreflightCheck(recordId, capturedDraftId) {
    const adopt = aiReplyState.adoptContext;
    if (!adopt || Number(adopt.recordId) !== Number(recordId)) {
        return;
    }
    if (Number(adopt.draftId) !== Number(capturedDraftId)) {
        return;
    }
    const editor = $("#manualRichReplyEditor");
    if (!editor) return;
    const textBody = editor.innerText || "";
    if (!textBody.trim()) {
        const container = $("#manualReplyPreflight");
        if (container) {
            container.hidden = true;
            container.innerHTML = "";
        }
        return;
    }
    const factRuleIds = adopt.qaRuleIds || (manualReplyQaContext?.qaRuleIds || []);
    const expectedEvidenceSetVersion = adopt.evidenceSetVersion || "";
    const detailId = Number(recordId);
    const seq = ++preflightState.seq;
    preflightState.loading = true;
    const container = $("#manualReplyPreflight");
    if (container) {
        container.hidden = false;
        container.innerHTML = `<div class="ai-reply-coverage">正在复验当前全文…</div>`;
    }
    try {
        const result = await api(`/api/mail/unmatched-inbound/${recordId}/composed-reply/preflight`, {
            method: "POST",
            body: JSON.stringify({
                factRuleIds,
                expectedEvidenceSetVersion,
                textBody
            })
        });
        if (seq !== preflightState.seq) return;
        const currentAdopt = aiReplyState.adoptContext;
        const currentEditor = $("#manualRichReplyEditor");
        const currentText = currentEditor ? currentEditor.innerText || "" : "";
        const stillSameRecord = currentAdopt && Number(currentAdopt.recordId) === Number(recordId);
        const stillSameDraft = stillSameRecord && Number(currentAdopt.draftId) === Number(adopt.draftId)
            && arraysEqual(currentAdopt.qaRuleIds, factRuleIds);
        const stillSameText = currentText === textBody;
        const stillCurrent = stillSameRecord && stillSameDraft && stillSameText
            && detailId === Number(state.mailbox.detailContext?.id);
        if (!stillCurrent) {
            if (container) {
                container.hidden = true;
                container.innerHTML = "";
            }
            return;
        }
        renderPreflightResult(result, container);
    } catch (e) {
        if (seq !== preflightState.seq) return;
        const currentAdopt = aiReplyState.adoptContext;
        const currentEditor = $("#manualRichReplyEditor");
        const currentText = currentEditor ? currentEditor.innerText || "" : "";
        const stillSameRecord = currentAdopt && Number(currentAdopt.recordId) === Number(recordId);
        const stillSameDraft = stillSameRecord && Number(currentAdopt.draftId) === Number(adopt.draftId);
        const stillSameText = currentText === textBody;
        const stillCurrent = stillSameRecord && stillSameDraft && stillSameText
            && detailId === Number(state.mailbox.detailContext?.id);
        if (stillCurrent && container) {
            container.hidden = false;
            container.innerHTML = `<div class="ai-reply-error">${escapeHtml(PREFLIGHT_UNAVAILABLE_TEXT)}</div>`;
        } else if (container) {
            container.hidden = true;
            container.innerHTML = "";
        }
    } finally {
        preflightState.loading = false;
    }
}

function arraysEqual(a, b) {
    if (!a || !b) return (!a || a?.length === 0) && (!b || b?.length === 0);
    if (a.length !== b.length) return false;
    return a.every((v, i) => v === b[i]);
}

function renderPreflightResult(result, container) {
    if (!container) return;
    if (!result) {
        container.hidden = true;
        container.innerHTML = "";
        return;
    }
    const warnings = Array.isArray(result.warningCodes) ? result.warningCodes : [];
    if (result.status === "PASS" && warnings.length === 0) {
        container.hidden = false;
        container.innerHTML = `<div class="ai-reply-coverage">${escapeHtml(PREFLIGHT_PASS_TEXT)}</div>`;
        return;
    }
    const parts = [];
    warnings.forEach((code) => {
        const label = AI_REPLY_WARNING_LABELS[code] || `发现未分类风险，请人工核对`;
        parts.push(`<div class="ai-reply-warning">${escapeHtml(label)}</div>`);
    });
    container.hidden = false;
    container.innerHTML = parts.join("");
}

async function showUnmatchedDetail(id) {
    manualReplyQaContext = null;
    aiReplyState.adoptContext = null;
    resetPreflightState();
    state.mailbox.detailContext = {
        source: "INBOUND_PROCESSING",
        id: Number(id),
        inboundProcessingId: Number(id)
    };
    const detailPromise = api(`/api/mail/unmatched-inbound/${id}`);
    const [data, logs, threadData] = await Promise.all([
        detailPromise,
        api(`/api/operator-action-logs?inboundProcessingId=${id}&pageSize=50&pageOffset=0`).catch(() => ({ records: [] })),
        api(`/api/inbound-summary/mails/${id}/thread`).catch(() => ({ tags: [] }))
    ]);
    const inboundTags = threadData.tags || [];
    const record = data.record;
    const suggest = record.expertContactId
        ? await api(`/api/mail/unmatched-inbound/${id}/composed-reply/suggest`).catch(() => null)
        : null;
    const history = record.expertContactId
        ? await api(`/api/expert-contacts/${record.expertContactId}`).catch(() => null)
        : null;
    const candidates = data.candidates || [];
    const contact = data.contact;
    const processingExpertTags = contact?.orcidId
        ? await fetchExpertTagsFromEs(contact.orcidId, contact.currentIndexLevel || "CANDIDATE")
        : [];
    const processingExpertTagHtml = renderMailboxExpertTagEditor(
        contact,
        processingExpertTags,
        "mailboxProcessingExpertTagEditor"
    );
    const panel = $("#unmatchedDetailPanel");
    panel.hidden = false;

    const linkedExpertHtml = record.expertContactId && contact ? `
        <div class="mail-expert-overview-expert">
            <div class="mail-expert-identity">
                <div class="candidate-info">
                    <strong>${escapeHtml(contact.expertName || record.expertName || "?")}</strong>
                    <span>${escapeHtml(contact.expertEmail || "-")}</span>
                    <span class="text-muted">ORCID: ${escapeHtml(contact.orcidId || "-")}</span>
                </div>
                <div class="candidate-meta">
                    <span>${badge(operatorStatusLabels[contact.operatorStatus] || contact.operatorStatus || "?", "ok")}</span>
                    <span>${badge(indexLevelLabels[contact.currentIndexLevel] || contact.currentIndexLevel || "?", "")}</span>
                    <button class="button" data-action="open-contact-from-unmatched" data-id="${record.expertContactId}">查看专家详情</button>
                </div>
            </div>
            <div class="mail-expert-controls">
                <label>
                    <span>专家状态</span>
                    <select id="unmatchedOperatorStatusSelect" data-record-id="${id}" data-current-value="${escapeHtml(contact.operatorStatus || "")}">
                        ${optionsFromArray(operatorStatusOptions, false, "请选择", contact.operatorStatus || "")}
                    </select>
                </label>
                <label>
                    <span>专家层级</span>
                    <select id="unmatchedIndexLevelSelect" data-record-id="${id}" data-current-value="${escapeHtml(contact.currentIndexLevel || "")}">
                        ${optionsFromArray(indexLevelOptions, false, "请选择", contact.currentIndexLevel || "")}
                    </select>
                </label>
                <button class="button primary" data-action="save-expert-changes" data-record-id="${id}">保存变更</button>
            </div>
            ${processingExpertTagHtml}
        </div>
    ` : `
        <div class="detail-section">
            <h3>候选推荐联系人</h3>
            <div class="candidates-list">${candidates.map((c) => `
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
            `).join("") || "<p class='text-muted'>暂无系统推荐，请手动搜索联系人。</p>"}</div>
            <div class="detail-section" style="margin-top:12px;">
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

    const composeWorkbenchHtml = suggest ? renderComposedReplyWorkbenchHtml(suggest, id) : "";

    const historyMails = (history && history.mails) || [];
    const historyTimes = historyMails.map(formatMailTime).filter(Boolean).sort();
    const latestHistoryTime = historyTimes.length ? historyTimes[historyTimes.length - 1] : "";
    const historyHtml = record.expertContactId && historyMails.length ? `
        <details class="detail-section reply-workflow-detail mail-history-detail">
            <summary class="reply-workflow-summary">
                <span class="reply-workflow-icon" aria-hidden="true">↺</span>
                <span class="reply-workflow-title"><strong>与该专家的历史信件记录</strong><small>${latestHistoryTime ? `最近联系：${escapeHtml(latestHistoryTime)}` : "查看完整往来"}</small></span>
                <span class="reply-workflow-status">${historyMails.length} 封</span>
                <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
            </summary>
            <div class="reply-workflow-content mail-timeline">
                ${historyMails.slice().reverse().map(renderMailItem).join("")}
            </div>
        </details>
    ` : "";

    panel.innerHTML = `
        <div class="panel-head">
            <h2>来信详情与处理</h2>
            <button class="button secondary" data-action="close-unmatched-detail">关闭</button>
        </div>
        <div class="unmatched-detail-body">
            <div class="mail-detail-group-label"><span>基本信息</span></div>
            <div class="mail-expert-overview">
                <div class="mail-overview-head">
                    <div class="mail-overview-subject">
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

            <details class="detail-section reply-workflow-detail auto-reply-preview-section" data-record-id="${id}">
                <summary class="reply-workflow-summary">
                    <span class="reply-workflow-icon" aria-hidden="true">自</span>
                    <span class="reply-workflow-title"><strong>自动回复预览</strong><small id="autoReplyPreviewMeta">正在分析来信意图与回复规则</small></span>
                    <span class="reply-workflow-status" id="autoReplyPreviewStatus">生成中…</span>
                    <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
                </summary>
                <div class="reply-workflow-content">
                    <p class="text-muted" style="font-size:12px;margin:0 0 8px;">模拟「若此刻开启自动回复」系统会回什么（不发送、不写库）</p>
                    <button type="button" class="button" data-action="preview-auto-reply" data-record-id="${id}">重新预览</button>
                    <div id="autoReplyPreviewResult" style="margin-top:12px;"><p class="text-muted">加载预览中…</p></div>
                </div>
            </details>

            ${composeWorkbenchHtml}

            <details class="detail-section reply-workflow-detail manual-rich-reply-section">
                <summary class="reply-workflow-summary">
                    <span class="reply-workflow-icon" aria-hidden="true">✎</span>
                    <span class="reply-workflow-title"><strong>人工富文本回复</strong><small>手动编辑主题和正文后发送</small></span>
                    <span class="reply-workflow-status">未填写</span>
                    <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
                </summary>
                <div class="reply-workflow-content">
                <input id="manualReplySubject" placeholder="邮件主题" style="margin-bottom:8px;">
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
                    <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
                </summary>
                <div class="reply-workflow-content">
                ${renderOperatorLogs(logs)}
                </div>
            </details>
        </div>
    `;

    if (suggest) {
        composedReplyState.recordId = id;
        composedReplyState.contactId = record.expertContactId || null;
        composedReplyState.contactOrcid = contact?.orcidId || null;
        initComposedReplyWorkbench(id, suggest);
    }

    loadAutoReplyPreview(id).catch(() => {});

    focusMailboxProcessingPanel();
}

async function handleUnmatchedAction(element) {
    const action = element.dataset.action;
    const id = element.dataset.id || element.dataset.recordId;

    if (action === "view-unmatched" || action === "open-pending") {
        await showUnmatchedDetail(id);
        return;
    }
    if (action === "close-unmatched-detail") {
        $("#unmatchedDetailPanel").hidden = true;
        state.mailbox.detailContext = null;
        return;
    }
    if (action === "preview-auto-reply") {
        try {
            await loadAutoReplyPreview(id);
        } catch (error) {
            showStatus(error.message || "预览失败", "error");
        }
        return;
    }
    if (action === "open-contact-from-unmatched") {
        await openContactInList(Number(id));
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
        await refreshMailboxAfterPendingAction();
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
        await refreshMailboxAfterPendingAction();
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
        await refreshMailboxAfterPendingAction();
        return;
    }
    if (action === "save-expert-changes") {
        const statusSelect = $("#unmatchedOperatorStatusSelect");
        const levelSelect = $("#unmatchedIndexLevelSelect");
        const newStatus = statusSelect?.value;
        const newLevel = levelSelect?.value;
        const currentStatus = statusSelect?.dataset.currentValue || "";
        const currentLevel = levelSelect?.dataset.currentValue || "";
        const statusChanged = newStatus && newStatus !== currentStatus;
        const levelChanged = newLevel && newLevel !== currentLevel;
        if (!statusChanged && !levelChanged) {
            showStatus("专家状态和层级均未变化");
            return;
        }
        const operatorName = window.localStorage.getItem("operatorName") || "console";
        element.disabled = true;
        try {
            if (statusChanged) {
                await api(`/api/mail/unmatched-inbound/${id}/operator-status`, {
                    method: "POST",
                    body: JSON.stringify({ operatorStatus: newStatus, operatorName })
                });
            }
            if (levelChanged) {
                await api(`/api/mail/unmatched-inbound/${id}/index-level`, {
                    method: "POST",
                    body: JSON.stringify({ targetLevel: newLevel, operatorName })
                });
            }
            showStatus("专家信息已更新", "ok");
            await showUnmatchedDetail(id);
        } catch (e) {
            showStatus(`专家信息更新失败：${e.message}`, "error");
            element.disabled = false;
        }
        return;
    }
    if (action === "change-operator-status") {
        const newStatus = $("#unmatchedOperatorStatusSelect")?.value;
        if (!newStatus) {
            showStatus("请选择专家状态", "error");
            return;
        }
        const operatorName = window.localStorage.getItem("operatorName") || "console";
        try {
            await api(`/api/mail/unmatched-inbound/${id}/operator-status`, {
                method: "POST",
                body: JSON.stringify({ operatorStatus: newStatus, operatorName })
            });
            alert("专家状态变更成功");
        } catch (e) {
            alert("专家状态变更失败: " + e.message);
            return;
        }
        await showUnmatchedDetail(id);
        return;
    }
    if (action === "change-index-level") {
        const newLevel = $("#unmatchedIndexLevelSelect")?.value;
        if (!newLevel) {
            showStatus("请选择专家层级", "error");
            return;
        }
        const operatorName = window.localStorage.getItem("operatorName") || "console";
        try {
            await api(`/api/mail/unmatched-inbound/${id}/index-level`, {
                method: "POST",
                body: JSON.stringify({ targetLevel: newLevel, operatorName })
            });
            alert("专家层级变更成功");
        } catch (e) {
            alert("专家层级变更失败: " + e.message);
            return;
        }
        await showUnmatchedDetail(id);
        return;
    }
    if (action === "mailbox-auto-tags") {
        await mailboxAutoApplyTags(element);
        return;
    }
    if (action === "mailbox-add-tag-open") {
        showMailboxAddTagModal();
        return;
    }
    if (action === "mailbox-remove-tag") {
        await mailboxRemoveTag(Number(element.dataset.tagId));
        return;
    }
    if (action === "ai-reply-stop") {
        if (!aiReplyState.activeGeneration) return;
        element.disabled = true;
        element.textContent = "正在停止…";
        try {
            const result = await cancelActiveAiReplyGeneration({ abort: true });
            if (result.status === "TOO_LATE") {
                element.disabled = false;
                element.textContent = "停止生成";
                updateAiReplyLoadingMessage("生成已进入完成阶段，无法停止");
                return;
            }
            setAiReplyLoading($(".compose-draft.ai-chat-panel"), false);
            aiReplyState.inFlight = false;
            showStatus("已停止生成", "ok");
        } catch (error) {
            element.disabled = false;
            element.textContent = "停止生成";
            showStatus(`停止失败：${error.message || "未知错误"}`, "error");
        }
        return;
    }
    if (action === "trust-generate-draft") {
        if (aiReplyState.inFlight) {
            return;
        }
        const isFirstTurn = !composedReplyState.lockedFactIds;
        const factIds = isFirstTurn
            ? (confirmedCanonicalFactIds() || [])
            : [...composedReplyState.lockedFactIds];
        if (composedReplyState.evaluationPending) {
            showStatus("事实校验中，请稍候", "error");
            return;
        }
        if (!factIds.length) {
            showStatus("请至少选择一条事实", "error");
            return;
        }
        const instruction = $("#composedOperatorInstruction")?.value?.trim() || "";
        const turnsToSend = isFirstTurn ? [] : [...aiReplyState.turns];
        if (!isFirstTurn && !instruction) {
            showStatus("请输入修改要求", "error");
            return;
        }
        if (!isFirstTurn && instruction) {
            turnsToSend.push({
                assistantDraft: aiReplyState.lastDraftTemplate,
                operatorInstruction: instruction
            });
        }
        const expectedModel = readAiReplyModelSelection("#trustReplyModel", aiReplyState.selectedModel);
        aiReplyState.selectedModel = expectedModel;
        let timeoutSelection;
        try {
            timeoutSelection = resolveAiReplyTimeoutSelection();
        } catch (error) {
            showStatus(error.message, "error");
            return;
        }
        const generationId = createAiReplyGenerationId();
        const body = {
            turns: turnsToSend,
            qaRuleIds: factIds,
            model: expectedModel,
            generationId,
            llmAttemptTimeoutSeconds: timeoutSelection.attemptTimeoutSeconds,
            llmTotalTimeoutSeconds: timeoutSelection.totalPayload,
            _resolvedTotalTimeoutSeconds: timeoutSelection.totalTimeoutSeconds
        };
        if (isFirstTurn && instruction) {
            body.operatorInstruction = instruction;
        }
        const panel = $(".compose-draft.ai-chat-panel");
        const feedback = $("#trustReplyFeedback");
        const requestSeq = aiReplyState.requestSeq;
        const expectedRecordId = composedReplyState.recordId;
        const detailId = Number(id);
        aiReplyState.inFlight = true;
        aiReplyState.activeGeneration = {
            generationId,
            recordId: detailId,
            requestSeq,
            model: expectedModel,
            attemptTimeoutSeconds: timeoutSelection.attemptTimeoutSeconds,
            totalTimeoutSeconds: timeoutSelection.totalTimeoutSeconds,
            controller: null
        };
        aiReplyState.latestProgress = null;
        aiReplyState.lastProgressSeq = -1;
        renderAiReplyFeedback(feedback, null);
        setAiReplyLoading(panel, true, "AI 正在生成回复…", {
            stoppable: true,
            generationId,
            attemptTimeoutSeconds: timeoutSelection.attemptTimeoutSeconds,
            totalTimeoutSeconds: timeoutSelection.totalTimeoutSeconds
        });
        startAiReplyProgressTicker();
        updateTrustWorkbenchButtons();
        try {
            let result = null;
            let terminal = null;
            await postAiReplySse(id, body, {
                onTerminal: (event, data) => {
                    terminal = event;
                    if (event === "result") result = data;
                    if (event === "error") throw new Error(data?.message || "AI 生成失败");
                }
            });
            if (terminal === "cancelled") {
                showStatus("已停止生成", "ok");
                return;
            }
            if (!result) throw new Error("SSE 未返回完整结果");
            const currentModel = readAiReplyModelSelection("#trustReplyModel", aiReplyState.selectedModel);
            const stillCurrent = requestSeq === aiReplyState.requestSeq
                && expectedRecordId === composedReplyState.recordId
                && detailId === Number(state.mailbox.detailContext?.id)
                && expectedModel === currentModel
                && aiReplyState.activeGeneration?.model === expectedModel
                && aiReplyState.activeGeneration?.attemptTimeoutSeconds === timeoutSelection.attemptTimeoutSeconds
                && aiReplyState.activeGeneration?.totalTimeoutSeconds === timeoutSelection.totalTimeoutSeconds
                && aiReplyState.activeGeneration?.generationId === generationId;
            if (!stillCurrent) {
                return;
            }
            if (result.selectedModel !== expectedModel) {
                renderAiReplyFeedback(feedback, null, "模型响应与当前选择不一致，请重新生成");
                return;
            }
            const rawDraft = result.draftText || "";
            const renderedDraft = result.renderedDraftText || rawDraft;
            const isSuccess = isAiReplyGenerationSuccess(result);
            if (isSuccess) {
                if (isFirstTurn) {
                    composedReplyState.lockedFactIds = [...factIds];
                } else if (instruction) {
                    aiReplyState.turns.push({
                        assistantDraft: aiReplyState.lastDraftTemplate,
                        operatorInstruction: instruction
                    });
                }
                aiReplyState.lastDraftTemplate = rawDraft;
                aiReplyState.lastRenderedDraft = renderedDraft;
                aiReplyState.lastQaRuleIds = [...factIds];
                aiReplyState.firstTurnDone = true;
                if (!isFirstTurn && instruction) {
                    $("#composedOperatorInstruction").value = "";
                }
            }
            composedReplyState.draft = { raw: rawDraft, rendered: renderedDraft, result };
            renderComposedDraftPreview();
            renderAiReplyFeedback(feedback, result);
            showStatus(aiReplyGenerationStateLabel(result.generationState) || (result.usedLlm ? "可信草稿已生成" : "LLM 生成失败，QA 规则参考内容"));
        } catch (e) {
            const currentModel = readAiReplyModelSelection("#trustReplyModel", aiReplyState.selectedModel);
            const stillCurrent = requestSeq === aiReplyState.requestSeq
                && expectedRecordId === composedReplyState.recordId
                && detailId === Number(state.mailbox.detailContext?.id)
                && expectedModel === currentModel
                && aiReplyState.activeGeneration?.model === expectedModel
                && aiReplyState.activeGeneration?.attemptTimeoutSeconds === timeoutSelection.attemptTimeoutSeconds
                && aiReplyState.activeGeneration?.totalTimeoutSeconds === timeoutSelection.totalTimeoutSeconds
                && aiReplyState.activeGeneration?.generationId === generationId;
            if (stillCurrent) {
                renderAiReplyFeedback(feedback, null, e.message || "未知错误");
                showStatus(`生成失败：${e.message || "未知错误"}`, "error");
            }
        } finally {
            const currentModel = readAiReplyModelSelection("#trustReplyModel", aiReplyState.selectedModel);
            const stillCurrent = requestSeq === aiReplyState.requestSeq
                && expectedRecordId === composedReplyState.recordId
                && detailId === Number(state.mailbox.detailContext?.id)
                && expectedModel === currentModel
                && aiReplyState.activeGeneration?.model === expectedModel
                && aiReplyState.activeGeneration?.attemptTimeoutSeconds === timeoutSelection.attemptTimeoutSeconds
                && aiReplyState.activeGeneration?.totalTimeoutSeconds === timeoutSelection.totalTimeoutSeconds
                && aiReplyState.activeGeneration?.generationId === generationId;
            if (stillCurrent) {
                setAiReplyLoading(panel, false);
                aiReplyState.inFlight = false;
                if (aiReplyState.activeGeneration?.generationId === generationId) {
                    aiReplyState.activeGeneration = null;
                }
                stopAiReplyProgressTicker();
                aiReplyState.latestProgress = null;
                updateTrustWorkbenchButtons();
            }
        }
        return;
    }
    if (action === "trust-adopt-draft") {
        resetPreflightState();
        const draft = composedReplyState.draft;
        const rendered = draft?.rendered || "";
        const raw = draft?.raw || "";
        if (!rendered) {
            showStatus("请先生成可信草稿", "error");
            return;
        }
        const draftResult = draft?.result;
        if (!draftResult || !isAiReplyGenerationSuccess(draftResult)) {
            showStatus("当前为 QA 规则参考内容，不可直接采用或发送。请重试生成或人工撰写。", "error");
            return;
        }
        if (composedReplyState.evaluationPending) {
            showStatus("事实校验中，请稍候", "error");
            return;
        }
        const factIds = composedReplyState.lockedFactIds
            || confirmedCanonicalFactIds()
            || [];
        if (!factIds.length) {
            showStatus("当前事实集合未确认，请重新选择事实", "error");
            return;
        }
        const draftEvidenceSetVersion = draft?.result?.evidenceSetVersion ?? "";
        const editor = $("#manualRichReplyEditor");
        if (editor) {
            editor.innerText = rendered;
        }
        aiReplyState.adoptContext = {
            rawTemplate: raw,
            renderedBaseline: editor ? editor.innerText : rendered,
            renderedBaselineHtml: editor ? editor.innerHTML : "",
            recordId: Number(id),
            draftId: -1,
            needsGroundingReview: false,
            reviewItems: [],
            draftReadiness: draft?.result?.draftReadiness || composedReplyState.evaluation?.draftReadiness || "READY",
            requestCount: Number(draft?.result?.requestCount) || 0,
            mode: draft?.result?.mode || "",
            qaRuleIds: [...factIds],
            evidenceSetVersion: draftEvidenceSetVersion
        };
        if (factIds.length > 0) {
            manualReplyQaContext = {
                qaRuleIds: [...factIds],
                baselineText: rendered
            };
            showStatus("草稿已填入人工富文本回复区，请填写主题后发送");
        } else {
            manualReplyQaContext = null;
            showStatus("草稿已填入人工富文本回复区，请填写主题后发送");
        }
        editor?.closest(".detail-section")?.scrollIntoView({ behavior: "smooth" });
        schedulePreflightCheck();
        return;
    }
    if (action === "ai-reply-turn") {
        if (aiReplyState.inFlight) {
            return;
        }
        const input = $("#aiChatInput");
        const instruction = input?.value?.trim() || "";
        const isFirstTurn = !aiReplyState.firstTurnDone;
        const turnsToSend = [...aiReplyState.turns];
        if (!isFirstTurn) {
            if (!instruction) {
                showStatus("请输入修改要求", "error");
                return;
            }
            turnsToSend.push({
                assistantDraft: aiReplyState.lastDraftTemplate,
                operatorInstruction: instruction
            });
        }
        const expectedModel = readAiReplyModelSelection("#aiMailboxReplyModel", aiReplyState.selectedModel);
        aiReplyState.selectedModel = expectedModel;
        const body = {
            turns: turnsToSend,
            qaRuleIds: isFirstTurn ? null : aiReplyState.lastQaRuleIds,
            model: expectedModel
        };
        if (isFirstTurn && instruction) {
            body.operatorInstruction = instruction;
        }
        const panel = $("#aiChatMessages")?.closest(".ai-chat-panel");
        const feedback = $("#aiReplyFeedback");
        const requestSeq = aiReplyState.requestSeq;
        const expectedRecordId = aiReplyState.recordId;
        const detailId = Number(id);
        aiReplyState.inFlight = true;
        renderAiReplyFeedback(feedback, null);
        setAiReplyLoading(panel, true);
        try {
            const result = await api(`/api/mail/unmatched-inbound/${id}/ai-reply/turn`, {
                method: "POST",
                body: JSON.stringify(body)
            });
            const currentModel = readAiReplyModelSelection("#aiMailboxReplyModel", aiReplyState.selectedModel);
            const stillCurrent = requestSeq === aiReplyState.requestSeq
                && expectedRecordId === aiReplyState.recordId
                && detailId === Number(state.mailbox.detailContext?.id)
                && expectedModel === currentModel;
            if (!stillCurrent) {
                return;
            }
            if (result.selectedModel !== expectedModel) {
                renderAiReplyFeedback(feedback, null, "模型响应与当前选择不一致，请重新生成");
                return;
            }
            if (!isFirstTurn && instruction) {
                appendAiChatOperatorBubble(instruction);
            } else if (isFirstTurn && instruction) {
                appendAiChatOperatorBubble(instruction);
            }
            const rawDraft = result.draftText || "";
            const renderedDraft = result.renderedDraftText || rawDraft;
            appendAiChatDraftBubble(rawDraft, renderedDraft, result);
            if (isAiReplyGenerationSuccess(result)) {
                if (!isFirstTurn && instruction) {
                    aiReplyState.turns.push({
                        assistantDraft: aiReplyState.lastDraftTemplate,
                        operatorInstruction: instruction
                    });
                }
                aiReplyState.lastDraftTemplate = rawDraft;
                aiReplyState.lastRenderedDraft = renderedDraft;
                aiReplyState.lastQaRuleIds = result.qaRuleIds || [];
                if (isFirstTurn) {
                    aiReplyState.mode = result.mode || null;
                    aiReplyState.firstTurnDone = true;
                }
            }
            renderAiReplyFeedback(feedback, result);
            if (input && isAiReplyGenerationSuccess(result)) input.value = "";
            const successLabel = aiReplyGenerationStateLabel(result.generationState);
            const showLabel = result.usedLlm
                ? (successLabel || "AI 生成完成")
                : "LLM 生成失败，QA 规则参考内容";
            const qaCount = (result.qaRuleIds || []).length;
            const modeHint = result.mode === "QA_MATCHED"
                ? `已匹配 QA 规则（${qaCount} 条），按规则拼接`
                : result.mode === "QA_GROUNDED"
                    ? `已基于 ${qaCount} 条 QA 事实综合多项请求`
                    : "未匹配 QA 规则，依据历史邮件/专家画像自由生成";
            showStatus(`${showLabel} — ${modeHint}`);
        } catch (e) {
            const currentModel = readAiReplyModelSelection("#aiMailboxReplyModel", aiReplyState.selectedModel);
            const stillCurrent = requestSeq === aiReplyState.requestSeq
                && expectedRecordId === aiReplyState.recordId
                && detailId === Number(state.mailbox.detailContext?.id)
                && expectedModel === currentModel;
            if (stillCurrent) {
                renderAiReplyFeedback(feedback, null, e.message || "未知错误");
            }
            showStatus(`AI 生成失败：${e.message || "未知错误"}`, "error");
        } finally {
            const currentModel = readAiReplyModelSelection("#aiMailboxReplyModel", aiReplyState.selectedModel);
            const stillCurrent = requestSeq === aiReplyState.requestSeq
                && expectedRecordId === aiReplyState.recordId
                && expectedModel === currentModel;
            if (stillCurrent) {
                setAiReplyLoading(panel, false);
                aiReplyState.inFlight = false;
            }
        }
        return;
    }
    if (action === "ai-adopt-draft") {
        const draftId = Number(element.dataset.draftId);
        resetPreflightState();
        const entry = aiReplyState.drafts[draftId];
        const rendered = entry?.rendered ?? aiReplyState.lastRenderedDraft;
        const raw = entry?.raw ?? aiReplyState.lastDraftTemplate;
        if (!rendered) {
            showStatus("草稿为空", "error");
            return;
        }
        if (!entry) {
            showStatus("草稿不存在或已过期", "error");
            return;
        }
        if (entry.usedLlm !== true || entry.generationState !== "LLM_USED") {
            showStatus("当前草稿由 LLM 失败回退生成，不可采用。请使用成功的 AI 草稿或人工撰写。", "error");
            return;
        }
        const qaIds = entry?.qaRuleIds ?? aiReplyState.lastQaRuleIds;
        const draftEvidenceSetVersion = entry?.evidenceSetVersion ?? "";
        const editor = $("#manualRichReplyEditor");
        if (editor) {
            editor.innerText = rendered;
        }
        aiReplyState.adoptContext = {
            rawTemplate: raw || "",
            renderedBaseline: editor ? editor.innerText : rendered,
            renderedBaselineHtml: editor ? editor.innerHTML : "",
            recordId: Number(id),
            draftId: draftId,
            needsGroundingReview: !!entry?.needsGroundingReview,
            reviewItems: Array.isArray(entry?.reviewItems) ? entry.reviewItems : [],
            draftReadiness: entry?.draftReadiness || "READY",
            requestCount: Number(entry?.requestCount) || 0,
            mode: entry?.mode || "",
            qaRuleIds: [...(qaIds || [])],
            evidenceSetVersion: draftEvidenceSetVersion
        };
        if (qaIds && qaIds.length > 0) {
            manualReplyQaContext = {
                qaRuleIds: [...qaIds],
                suggestedRuleIds: [...qaIds],
                freeText: null,
                ackSnippetId: null,
                baselineText: rendered
            };
            showStatus("草稿已填入人工富文本回复区");
        } else {
            manualReplyQaContext = null;
            showStatus("草稿已填入人工富文本回复区，请填写主题后发送");
        }
        editor?.closest(".detail-section")?.scrollIntoView({ behavior: "smooth" });
        schedulePreflightCheck();
        return;
    }

    async function submitManualRichReply(recordId, requestBody) {
        try {
            await api(`/api/mail/unmatched-inbound/${recordId}/manual-rich-reply`, {
                method: "POST",
                body: JSON.stringify(requestBody)
            });
            manualReplyQaContext = null;
            aiReplyState.adoptContext = null;
            resetPreflightState();
            alert("人工回复邮件发送成功");
        } catch (e) {
            alert("人工回复发送失败: " + e.message);
            throw e;
        }
        await showUnmatchedDetail(recordId);
        await refreshMailboxAfterPendingAction();
    }

    if (action === "send-manual-rich-reply") {
        const editor = $("#manualRichReplyEditor");
        const subject = $("#manualReplySubject")?.value?.trim();
        if (!subject) {
            showStatus("请输入邮件主题", "error");
            return;
        }
        if (!editor || !editor.innerHTML.trim()) {
            showStatus("请输入邮件正文", "error");
            return;
        }
        const adopt = aiReplyState.adoptContext;
        const operatorName = window.localStorage.getItem("operatorName") || "console";
        const requestBody = {
            senderAccountCode: null,
            subject,
            htmlBody: editor.innerHTML,
            textBody: editor.innerText,
            operatorName
        };
        if (
            adopt
            && Number(adopt.recordId) === Number(id)
            && (adopt.rawTemplate || "").trim()
            && editor.innerText.trim() === (adopt.renderedBaseline || "").trim()
            && editor.innerHTML === (adopt.renderedBaselineHtml || "")
        ) {
            requestBody.templateTextBody = adopt.rawTemplate;
        }
        if (manualReplyQaContext?.qaRuleIds?.length) {
            requestBody.qaRuleIds = manualReplyQaContext.qaRuleIds;
            requestBody.edited = editor.innerText.trim() !== (manualReplyQaContext.baselineText || "").trim();
        }

        return submitManualRichReply(id, requestBody);
    }
    if (action === "rich-command") {
        const command = element.dataset.command;
        if (command === "createLink") {
            const url = prompt("请输入链接 URL:");
            if (url) document.execCommand(command, false, url);
        } else {
            document.execCommand(command, false, null);
        }
        return;
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
    const dateInput = $("#monitoringDate");
    if (dateInput && !state.monitoring.date) {
        state.monitoring.date = monitoringToday();
    }
    if (dateInput) {
        dateInput.value = state.monitoring.date || monitoringToday();
    }
    const dateParams = new URLSearchParams();
    if (state.monitoring.date) dateParams.set("date", state.monitoring.date);
    const [summary, senderHealth, providerDistribution, regionDistribution] = await Promise.all([
        api(`/api/mail-monitoring/summary?${dateParams}`),
        api(`/api/mail-monitoring/sender-accounts?${dateParams}`),
        api(`/api/mail-monitoring/provider-distribution?${dateParams}`).catch(() => []),
        api(`/api/mail-monitoring/region-distribution?${dateParams}`).catch(() => [])
    ]);
    state.monitoring.summary = summary;
    state.monitoring.senderHealth = senderHealth || [];
    state.monitoring.providerDistribution = providerDistribution || [];
    state.monitoring.regionDistribution = regionDistribution || [];
    renderMonitoringCards();
    renderMonitoringProviderDistribution();
    renderMonitoringRegionDistribution();
    await loadMonitoringReputation();
    renderMonitoringSenderHealth();
    renderMonitoringSenderOptions();
    await loadMonitoringSubTab();
    state.monitoring.lastRefreshedAt = new Date();
    renderMonitoringLastRefreshed();
    scheduleMonitoringAutoRefresh();
}

function monitoringRangeParams() {
    const params = new URLSearchParams();
    const date = state.monitoring.date || monitoringToday();
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
    const providers = state.monitoring.providerDistribution || [];
    const regions = state.monitoring.regionDistribution || [];
    const activeProviderCount = providers.filter((row) => (row.sentCount || 0) > 0).length;
    const activeRegionCount = regions.filter((row) => (row.sentCount || 0) > 0).length;
    const worstBounceProvider = providers
        .filter((row) => (row.sentCount || 0) > 0)
        .map((row) => ({
            provider: row.provider,
            rate: (row.hardBounceCount || 0) / row.sentCount
        }))
        .sort((a, b) => b.rate - a.rate)[0];
    const cards = [
        ["今日介绍邮件", s.introductions, null],
        ["今日收到回复", s.inboundReplies, null],
        ["今日回复专家数", s.repliedExperts, null],
        ["今日自动回复", s.autoReplies, "会议邀约为自动回复的子项，细分统计不可相加"],
        ["今日人工外发", s.operatorOutbound, null],
        ["今日会议邀约", s.meetingInvitations, "属于自动回复子项，细分统计不可相加"],
        ["今日人工待办新增", s.manualReviewInbound, "未匹配来信为人工待办子项，细分统计不可相加"],
        ["今日未匹配来信", s.unmatchedInbound, "属于人工待办子项，细分统计不可相加"],
        ["今日发送失败", s.failedOutbound, null],
        ["今日 APPLICATION 晋级", s.applicationPromotions, null],
        ["覆盖地区数", activeRegionCount, "当日有首发邮件的大区数"],
        ["覆盖服务商数", activeProviderCount, "当日有首发邮件的服务商桶数"],
        ["最高退信服务商", worstBounceProvider
            ? `${worstBounceProvider.provider} (${formatPercent(worstBounceProvider.rate)})`
            : "-", "按硬退率（硬退数/发送量）"]
    ];
    $("#monitoringCards").innerHTML = cards.map(([label, value, hint]) => `
        <div class="metric-card"${hint ? ` title="${escapeHtml(hint)}"` : ""}>
            <div class="metric-label">${escapeHtml(label)}${hint ? '<span class="metric-hint" title="' + escapeHtml(hint) + '">ⓘ</span>' : ""}</div>
            <div class="metric-value">${escapeHtml(value ?? 0)}</div>
        </div>
    `).join("");
}

function formatPercent(value) {
    if (value == null || Number.isNaN(value)) return "0%";
    return `${(value * 100).toFixed(1)}%`;
}

function monitoringDistributionBar(value, maxValue) {
    const width = maxValue > 0 ? Math.max(4, Math.round((value / maxValue) * 100)) : 0;
    return `<div style="background:#e2e8f0;border-radius:999px;height:8px;width:100%;max-width:120px;">
        <div style="background:linear-gradient(90deg,#3b82f6,#60a5fa);height:8px;border-radius:999px;width:${width}%;"></div>
    </div>`;
}

function renderMonitoringProviderDistribution() {
    const table = $("#monitoringProviderDistributionTable");
    if (!table) return;
    const rows = state.monitoring.providerDistribution || [];
    const maxSent = Math.max(0, ...rows.map((row) => row.sentCount || 0));
    table.querySelector("thead").innerHTML = `
        <tr>
            <th>服务商</th><th>发送量</th><th>发送</th><th>回复率</th><th>硬退率</th><th>软退</th>
        </tr>
    `;
    table.querySelector("tbody").innerHTML = rows.map((row) => {
        const hardRate = row.sentCount > 0 ? (row.hardBounceCount || 0) / row.sentCount : 0;
        return `<tr>
            <td><strong>${escapeHtml(row.provider)}</strong></td>
            <td>${monitoringDistributionBar(row.sentCount || 0, maxSent)}</td>
            <td>${escapeHtml(row.sentCount ?? 0)}</td>
            <td>${escapeHtml(formatPercent(row.replyRate))}</td>
            <td>${escapeHtml(formatPercent(hardRate))}</td>
            <td>${escapeHtml(row.softBounceCount ?? 0)}</td>
        </tr>`;
    }).join("") || `<tr><td colspan="6" class="text-muted" style="text-align:center;">暂无数据</td></tr>`;
}

function renderMonitoringRegionDistribution() {
    const table = $("#monitoringRegionDistributionTable");
    if (!table) return;
    const rows = state.monitoring.regionDistribution || [];
    const maxSent = Math.max(0, ...rows.map((row) => row.sentCount || 0));
    table.querySelector("thead").innerHTML = `
        <tr>
            <th>地区</th><th>发送量</th><th>发送</th><th>回复率</th><th>晋级</th>
        </tr>
    `;
    table.querySelector("tbody").innerHTML = rows.map((row) => `
        <tr>
            <td><strong>${escapeHtml(row.region)}</strong></td>
            <td>${monitoringDistributionBar(row.sentCount || 0, maxSent)}</td>
            <td>${escapeHtml(row.sentCount ?? 0)}</td>
            <td>${escapeHtml(formatPercent(row.replyRate))}</td>
            <td>${escapeHtml(row.promotionCount ?? 0)}</td>
        </tr>
    `).join("") || `<tr><td colspan="5" class="text-muted" style="text-align:center;">暂无数据</td></tr>`;
}

async function loadMonitoringReputation() {
    const domainSelect = $("#monitoringReputationDomain");
    const daysSelect = $("#monitoringReputationDays");
    if (!domainSelect || !daysSelect) return;
    const days = Number(daysSelect.value || state.monitoring.reputationDays || 30);
    state.monitoring.reputationDays = days;
    const params = new URLSearchParams({ days: String(days) });
    if (state.monitoring.reputationDomain) {
        params.set("domain", state.monitoring.reputationDomain);
    }
    try {
        const data = await api(`/api/mail-monitoring/reputation-history?${params}`);
        state.monitoring.reputationHistory = data.history || [];
        state.monitoring.reputationDomains = data.domains || [];
        if (data.domain) {
            state.monitoring.reputationDomain = data.domain;
        }
        if (!state.monitoring.reputationDomain && state.monitoring.reputationDomains.length > 0) {
            state.monitoring.reputationDomain = state.monitoring.reputationDomains[0];
        }
        domainSelect.innerHTML = (state.monitoring.reputationDomains.length > 0
            ? state.monitoring.reputationDomains
            : [state.monitoring.reputationDomain].filter(Boolean)
        ).map((domain) =>
            `<option value="${escapeHtml(domain)}">${escapeHtml(domain)}</option>`
        ).join("") || `<option value="">暂无域名</option>`;
        domainSelect.value = state.monitoring.reputationDomain || "";
        daysSelect.value = String(days);
        renderMonitoringReputationChart();
    } catch (_) {
        state.monitoring.reputationHistory = [];
        renderMonitoringReputationChart();
    }
}

function renderMonitoringReputationChart() {
    const statusEl = $("#monitoringReputationStatus");
    const chartEl = $("#monitoringReputationChart");
    if (!statusEl || !chartEl) return;
    const rows = state.monitoring.reputationHistory || [];
    const domain = state.monitoring.reputationDomain || "-";
    const pausedAccounts = (state.monitoring.senderHealth || []).filter((row) =>
        row.autoSendPaused && String(row.autoSendPausedReason || "").startsWith("REPUTATION:")
    );
    const latest = rows[rows.length - 1];
    const latestSpam = latest?.spamRate != null ? formatPercent(latest.spamRate) : "-";
    statusEl.innerHTML = `
        当前域名：<strong>${escapeHtml(domain)}</strong>
        · 最新投诉率：${escapeHtml(latestSpam)}
        · REPUTATION 暂停账号：${escapeHtml(pausedAccounts.length)}
        ${pausedAccounts.length > 0 ? `（${escapeHtml(pausedAccounts.map((row) => row.accountCode).join(", "))}）` : ""}
    `;
    if (rows.length === 0) {
        chartEl.innerHTML = `<p class="text-muted" style="text-align:center; padding: 24px 0;">暂无 Postmaster 采集数据</p>`;
        return;
    }
    const width = Math.max(640, rows.length * 36);
    const height = 220;
    const padding = { top: 16, right: 16, bottom: 32, left: 48 };
    const plotWidth = width - padding.left - padding.right;
    const plotHeight = height - padding.top - padding.bottom;
    const maxRate = Math.max(0.004, ...rows.map((row) => row.spamRate ?? 0));
    const pauseLine = 0.003;
    const resumeLine = 0.001;
    const xStep = rows.length > 1 ? plotWidth / (rows.length - 1) : 0;
    const toX = (index) => padding.left + index * xStep;
    const toY = (rate) => padding.top + plotHeight - (rate / maxRate) * plotHeight;
    const points = rows.map((row, index) => `${toX(index)},${toY(row.spamRate ?? 0)}`).join(" ");
    const pauseY = toY(pauseLine);
    const resumeY = toY(resumeLine);
    const labels = rows.map((row, index) => {
        const x = toX(index);
        return `<text x="${x}" y="${height - 8}" text-anchor="middle" font-size="10" fill="#64748b">${escapeHtml((row.date || "").slice(5))}</text>`;
    }).join("");
    chartEl.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" width="100%" height="${height}" role="img" aria-label="域投诉率趋势">
            <line x1="${padding.left}" y1="${pauseY}" x2="${width - padding.right}" y2="${pauseY}" stroke="#ef4444" stroke-dasharray="4 4" />
            <text x="${width - padding.right}" y="${pauseY - 4}" text-anchor="end" font-size="10" fill="#ef4444">暂停线 0.3%</text>
            <line x1="${padding.left}" y1="${resumeY}" x2="${width - padding.right}" y2="${resumeY}" stroke="#22c55e" stroke-dasharray="4 4" />
            <text x="${width - padding.right}" y="${resumeY - 4}" text-anchor="end" font-size="10" fill="#22c55e">恢复线 0.1%</text>
            <polyline fill="none" stroke="#3b82f6" stroke-width="2" points="${points}" />
            ${rows.map((row, index) => `<circle cx="${toX(index)}" cy="${toY(row.spamRate ?? 0)}" r="3" fill="#2563eb" />`).join("")}
            ${labels}
            <text x="8" y="${padding.top + 8}" font-size="10" fill="#64748b">${escapeHtml(formatPercent(maxRate))}</text>
            <text x="8" y="${padding.top + plotHeight}" font-size="10" fill="#64748b">0%</text>
        </svg>
    `;
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
    if (tab === "bounces") {
        const bounceParams = new URLSearchParams();
        bounceParams.set("pageSize", state.monitoring.pageSize);
        bounceParams.set("pageOffset", state.monitoring.page * state.monitoring.pageSize);
        const accountCode = $("#monitoringSenderAccount")?.value;
        if (accountCode) bounceParams.set("accountCode", accountCode);
        url = `/api/mail/bounces?${bounceParams}`;
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
    if (tab === "bounces") {
        table.querySelector("thead").innerHTML = `<tr><th>时间</th><th>账号</th><th>类型</th><th>失败收件人</th><th>失败原因</th><th>关联专家</th><th>DSN</th><th>原始 Message-ID</th></tr>`;
        table.querySelector("tbody").innerHTML = rows.map((r) => `
            <tr><td>${escapeHtml(r.receivedAt || "-")}</td><td>${escapeHtml(r.senderAccountCode || "-")}</td>
            <td>${badge(r.bounceType || "-", r.bounceType === "HARD" ? "error" : "warn")}</td>
            <td>${escapeHtml(r.failedRecipient || "-")}</td>
            <td>${escapeHtml(r.bounceReason || "-")}</td>
            <td>${escapeHtml(r.expertName || r.expertEmail || r.originalExpertContactId || "-")}</td>
            <td>${escapeHtml(r.dsnStatus || "-")}</td><td>${escapeHtml(r.originalMessageId || "-")}</td></tr>
        `).join("") || renderEmpty(8);
        const backfillBtn = $("#monitoringBounceBackfillBtn");
        if (backfillBtn) backfillBtn.style.display = "";
        return;
    }
    const backfillBtn = $("#monitoringBounceBackfillBtn");
    if (backfillBtn) backfillBtn.style.display = "none";
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
            if (appStarted === true && state.view === "monitoring") {
                scheduleMonitoringAutoRefresh();
            }
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
    $("#monitoringReputationDomain")?.addEventListener("change", (event) => {
        state.monitoring.reputationDomain = event.target.value || "";
        loadMonitoringReputation().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringReputationDays")?.addEventListener("change", (event) => {
        state.monitoring.reputationDays = Number(event.target.value || 30);
        loadMonitoringReputation().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringSubTabs").addEventListener("click", (event) => {
        const tab = event.target.closest("[data-subtab]");
        if (!tab) return;
        state.monitoring.subTab = tab.dataset.subtab;
        state.monitoring.page = 0;
        $$("#monitoringSubTabs .tab").forEach((item) => item.classList.toggle("active", item === tab));
        loadMonitoringSubTab().catch((e) => showStatus(e.message, "error"));
    });
    $("#monitoringBounceBackfillBtn")?.addEventListener("click", async () => {
        if (!confirm("将从 inbound 历史记录回填退信名单，可重复执行。继续？")) return;
        try {
            const result = await api("/api/mail/bounces/backfill", { method: "POST" });
            showStatus(`回填完成：扫描 ${result.scanned}，新增 ${result.ingested}，重复 ${result.duplicates}`);
            if (state.monitoring.subTab === "bounces") {
                await loadMonitoringSubTab();
            }
        } catch (e) {
            showStatus(e.message, "error");
        }
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
            await openContactInList(Number(target.dataset.id));
        }
        if (target.dataset.action === "view-unmatched" || target.dataset.action === "open-pending") {
            setView("mailbox");
            setMailboxPendingOnly(true);
            state.mailbox.page = 0;
            await loadMailbox();
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
    ensureTranslateClickHandler();
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
    document.querySelectorAll(".password-toggle").forEach((btn) => {
        btn.addEventListener("click", () => {
            const input = $("#accountForm").elements[btn.dataset.target];
            if (!input) return;
            const show = input.type === "password";
            input.type = show ? "text" : "password";
            btn.textContent = show ? "隐藏" : "显示";
        });
    });
    $("#copySmtpToImapBtn").addEventListener("click", () => {
        const form = $("#accountForm");
        if (form.imapHost.disabled) return;
        form.imapHost.value = (form.smtpHost.value || "").replace(/^smtp/i, "imap");
        form.imapPort.value = form.imapPort.value || 993;
        form.imapUsername.value = form.smtpUsername.value;
        form.imapPassword.value = form.smtpPassword.value;
    });
    $("#warmupEnabledCheckbox").addEventListener("change", updateWarmupFieldsVisibility);
    $("#warmupStepsMode").addEventListener("change", updateWarmupStepsMode);
    $("#deleteAccountBtn").addEventListener("click", async () => {
        const code = state.selectedAccount;
        if (!code) return;
        if (!confirm(`确认删除账号「${code}」？此操作不可恢复。`)) return;
        try {
            await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}`, { method: "DELETE" });
            showStatus(`账号 ${code} 已删除`, "ok");
            hideAccountEditor();
            await loadAccounts();
        } catch (error) {
            showStatus(error.message, "error");
        }
    });
    $("#accountsTable").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleAccountAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#reloadQaBtn").addEventListener("click", loadQa);
    const qaAuditFrom = $("#qaAuditFrom");
    const qaAuditTo = $("#qaAuditTo");
    if (qaAuditFrom && !qaAuditFrom.value) {
        const today = monitoringToday();
        qaAuditTo.value = today;
        const monthAgo = new Date();
        monthAgo.setDate(monthAgo.getDate() - 30);
        qaAuditFrom.value = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(monthAgo);
    }
    $("#loadQaAuditBtn")?.addEventListener("click", () => loadQaAuditReport().catch((e) => showStatus(e.message, "error")));
    $("#loadQaAuditBtn")?.addEventListener("click", () => loadQaAuditReport().catch((e) => showStatus(e.message, "error")));
    $("#qaRuleForm").addEventListener("submit", (event) => saveQaRule(event).catch((error) => showStatus(error.message, "error")));
    document.addEventListener("click", (event) => {
        const insertBtn = event.target.closest(".var-insert-btn");
        if (insertBtn) {
            event.preventDefault();
            event.stopPropagation();
            const wrap = insertBtn.closest(".var-insert-wrap");
            const menu = wrap?.querySelector(".var-insert-menu");
            if (!menu) return;
            const willOpen = menu.hidden;
            closeOpenVarInsertMenus(wrap);
            menu.hidden = !willOpen;
            return;
        }
        if (!event.target.closest(".var-insert-wrap")) {
            closeOpenVarInsertMenus();
        }
    });
    $("#previewRail")?.addEventListener("click", expandPreviewDrawer);
    $("#previewDrawerCloseBtn")?.addEventListener("click", collapsePreviewDrawer);
    $("#previewDrawerBackdrop")?.addEventListener("click", collapsePreviewDrawer);
    $("#previewRefreshBtn")?.addEventListener("click", () => {
        refreshPreviewDrawer().catch((error) => showStatus(error.message, "error"));
    });
    $("#previewDiceBtn")?.addEventListener("click", () => {
        randomPreviewExpert().catch((error) => showStatus(error.message, "error"));
    });
    $("#previewComposeRefreshBtn")?.addEventListener("click", () => {
        refreshPreviewDrawer().catch((error) => showStatus(error.message, "error"));
    });
    $("#previewComposeDiceBtn")?.addEventListener("click", () => {
        randomComposeTemplatePreviewExpert().catch((error) => showStatus(error.message, "error"));
    });
    $("#previewVariantPrev")?.addEventListener("click", () => stepPreviewVariantIndex(-1));
    $("#previewVariantNext")?.addEventListener("click", () => stepPreviewVariantIndex(1));
    $("#previewDrawer")?.addEventListener("keydown", (event) => {
        if (
            event.key === "Enter"
            && event.target?.matches?.("#previewComposeExpertInput, #previewComposeAccountInput")
        ) {
            event.preventDefault();
            refreshPreviewDrawer().catch((error) => showStatus(error.message, "error"));
        }
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#previewDrawer").hidden) {
            collapsePreviewDrawer();
        }
    });
    $("#newQaRuleBtn").addEventListener("click", () => fillQaRuleForm(null));
    $("#clearQaRuleBtn").addEventListener("click", hideQaRuleEditor);
    $("#qaRuleModalCloseBtn").addEventListener("click", hideQaRuleEditor);
    $("#qaRuleModalBackdrop").addEventListener("click", hideQaRuleEditor);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#qaRuleModal").hidden) {
            hideQaRuleEditor();
        }
    });
    $("#qaRulesTable").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleQaAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#reloadReplySnippetsBtn")?.addEventListener("click", () => {
        loadReplySnippets().catch((error) => showStatus(error.message, "error"));
    });
    $("#newReplySnippetBtn")?.addEventListener("click", () => fillReplySnippetForm(null));
    $("#replySnippetForm")?.addEventListener("submit", (event) => {
        saveReplySnippet(event).catch((error) => showStatus(error.message, "error"));
    });
    $("#clearReplySnippetBtn")?.addEventListener("click", hideReplySnippetEditor);
    $("#replySnippetModalCloseBtn")?.addEventListener("click", hideReplySnippetEditor);
    $("#replySnippetModalBackdrop")?.addEventListener("click", hideReplySnippetEditor);
    $("#replySnippetForm")?.snippetType?.addEventListener("change", () => {
        updateReplySnippetDefaultFieldVisibility();
    });
    $("#replySnippetForm")?.addEventListener("click", (event) => {
        handleContentVariantEditorClick(event, $("#replySnippetForm"));
    });
    $("#replySnippetForm")?.addEventListener("input", handleContentVariantEditorInput);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#replySnippetModal")?.hidden) {
            hideReplySnippetEditor();
        }
    });
    $("#replySnippetsPanels")?.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleReplySnippetAction(button).catch((error) => showStatus(error.message, "error"));
    });
    document.querySelectorAll("#view-mail-templates .mail-templates-tab").forEach((button) => {
        button.addEventListener("click", () => switchMailTemplatesSubTab(button.dataset.subTab));
    });
    $("#reloadComposeTemplatesBtn")?.addEventListener("click", () => {
        loadComposeTemplates().catch((error) => showStatus(error.message, "error"));
    });
    $("#newComposeTemplateBtn")?.addEventListener("click", () => openComposeTemplateEditor(null));
    $("#composeTemplateForm")?.addEventListener("submit", (event) => {
        saveComposeTemplate(event).catch((error) => showStatus(error.message, "error"));
    });
    $("#composeTemplateCancelBtn")?.addEventListener("click", hideComposeTemplateEditor);
    $("#composeTemplateModalCloseBtn")?.addEventListener("click", hideComposeTemplateEditor);
    $("#composeTemplateModalBackdrop")?.addEventListener("click", hideComposeTemplateEditor);
    $("#openComposeTemplatePreviewBtn")?.addEventListener("click", () => {
        openComposeTemplatePreview().catch((error) => showStatus(error.message, "error"));
    });
    $("#addComposeTemplateBlockBtn")?.addEventListener("click", () => {
        const blocks = collectComposeTemplateBlocksFromForm();
        blocks.push({ blockOrder: blocks.length, blockType: "CUSTOM_TEXT", refId: null, customText: "" });
        renderComposeTemplateBlockRows(blocks);
        schedulePreviewDrawerRefresh();
    });
    $("#composeTemplateBlocksList")?.addEventListener("click", handleComposeTemplateBlocksListClick);
    $("#composeTemplateBlocksList")?.addEventListener("change", handleComposeTemplateBlockTypeChange);
    $("#composeTemplateBlocksList")?.addEventListener("input", schedulePreviewDrawerRefresh);
    $("#composeTemplateSubject")?.addEventListener("input", schedulePreviewDrawerRefresh);
    $("#previewComposeExpertInput")?.addEventListener("input", schedulePreviewDrawerRefresh);
    $("#previewComposeAccountInput")?.addEventListener("input", schedulePreviewDrawerRefresh);
    document.querySelectorAll('input[name="previewComposePlaceholderMode"]').forEach((input) => {
        input.addEventListener("change", schedulePreviewDrawerRefresh);
    });
    $("#composeTemplatesTable")?.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleComposeTemplateAction(button).catch((error) => showStatus(error.message, "error"));
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#composeTemplateModal")?.hidden) {
            hideComposeTemplateEditor();
        }
    });
    $("#reloadSuppressionsBtn").addEventListener("click", () => {
        loadSuppressions().catch((error) => showStatus(error.message, "error"));
    });
    $("#searchSuppressionsBtn").addEventListener("click", () => {
        state.suppressionKeyword = $("#suppressionKeyword").value.trim();
        state.suppressionsPage = 0;
        loadSuppressions().catch((error) => showStatus(error.message, "error"));
    });
    $("#suppressionKeyword").addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            state.suppressionKeyword = $("#suppressionKeyword").value.trim();
            state.suppressionsPage = 0;
            loadSuppressions().catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#newSuppressionBtn").addEventListener("click", () => {
        showSuppressionEditor();
    });
    $("#suppressionForm").addEventListener("submit", (event) => {
        saveSuppression(event).catch((error) => showStatus(error.message, "error"));
    });
    $("#clearSuppressionBtn").addEventListener("click", hideSuppressionEditor);
    $("#suppressionModalCloseBtn").addEventListener("click", hideSuppressionEditor);
    $("#suppressionModalBackdrop").addEventListener("click", hideSuppressionEditor);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#suppressionModal").hidden) {
            hideSuppressionEditor();
        }
    });
    $("#suppressionsTable").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleSuppressionAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#suppressionPrevPage").addEventListener("click", () => {
        if (state.suppressionsPage > 0) {
            state.suppressionsPage -= 1;
            loadSuppressions().catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#suppressionNextPage").addEventListener("click", () => {
        state.suppressionsPage += 1;
        loadSuppressions().catch((error) => showStatus(error.message, "error"));
    });
    $("#loadContactsBtn").addEventListener("click", () => {
        loadContacts().catch((e) => showStatus(e.message, "error"));
    });
    $("#contactList").addEventListener("click", (event) => {
        const item = event.target.closest("[data-action]");
        if (item) handleContactAction(item).catch((error) => showStatus(error.message, "error"));
    });
    $("#contactDetail").addEventListener("click", (event) => {
        const subTabBtn = event.target.closest("[data-sub-tab]");
        if (subTabBtn) {
            activateDetailSubTab(subTabBtn);
            return;
        }
        const button = event.target.closest("button[data-action]");
        if (button) handleContactAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#aiAnalysisModal")?.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleContactAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#aiAnalysisModalBackdrop")?.addEventListener("click", closeAiAnalysisModal);
    $("#aiAnalysisModal")?.addEventListener("focusout", (event) => {
        const input = event.target.closest(".analysis-field-input[data-field-id]");
        if (!input) return;
        const fieldId = Number(input.dataset.fieldId);
        if (!Number.isFinite(fieldId)) return;
        saveAiAnalysisField(fieldId, input.value);
    });
    $("#contactHeadActions").addEventListener("click", async (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) {
            handleContactAction(button).catch((error) => showStatus(error.message, "error"));
            return;
        }

        if (event.target.closest("#saveContactChangesBtn")) {
            const saveBtn = $("#saveContactChangesBtn");
            const contactId = saveBtn.dataset.contactId;
            if (!contactId || saveBtn.disabled) return;

            const statusSelect = $("#operatorStatusSelect");
            const levelSelect = $("#indexLevelSelect");
            const replySelect = $("#autoReplySelect");

            const changes = [];
            if (statusSelect && statusSelect.value !== statusSelect.dataset.original) {
                const oldLabel = operatorStatusOptions.find(o => o[0] === statusSelect.dataset.original)?.[1] || statusSelect.dataset.original;
                const newLabel = operatorStatusOptions.find(o => o[0] === statusSelect.value)?.[1] || statusSelect.value;
                changes.push(`状态: ${oldLabel} → ${newLabel}`);
            }
            if (levelSelect && levelSelect.value !== levelSelect.dataset.original) {
                const oldLabel = indexLevelOptions.find(o => o[0] === levelSelect.dataset.original)?.[1] || levelSelect.dataset.original;
                const newLabel = indexLevelOptions.find(o => o[0] === levelSelect.value)?.[1] || levelSelect.value;
                changes.push(`层级: ${oldLabel} → ${newLabel}`);
            }
            if (replySelect && replySelect.value !== replySelect.dataset.original) {
                const oldLabel = replySelect.dataset.original === "auto" ? "自动回复" : "人工回复";
                const newLabel = replySelect.value === "auto" ? "自动回复" : "人工回复";
                changes.push(`回复模式: ${oldLabel} → ${newLabel}`);
            }

            if (changes.length === 0) return;

            const confirmed = await openActionDialog("confirm", {
                message: `确认以下变更？\n\n${changes.join("\n")}`
            });
            if (!confirmed) return;

            saveBtn.disabled = true;
            saveBtn.textContent = "保存中...";
            try {
                if (statusSelect && statusSelect.value !== statusSelect.dataset.original) {
                    await handleOperatorStatusChange(contactId, statusSelect.value);
                }
                if (levelSelect && levelSelect.value !== levelSelect.dataset.original) {
                    await handleIndexLevelChange(contactId, levelSelect.value);
                }
                if (replySelect && replySelect.value !== replySelect.dataset.original) {
                    if (replySelect.value === "manual") {
                        await api(`/api/expert-contacts/${contactId}/switch-to-manual`, {
                            method: "POST",
                            body: JSON.stringify({ reason: "OPERATOR_MANUAL", note: "控制台手动切换" })
                        });
                    } else {
                        await api(`/api/expert-contacts/${contactId}/switch-to-auto`, {
                            method: "POST",
                            body: JSON.stringify({ note: "控制台手动切换" })
                        });
                    }
                }
                showStatus("变更已保存", "ok");
                await loadContactDetail(contactId);
                await loadContacts();
            } catch (e) {
                showStatus("保存失败: " + e.message, "error");
                saveBtn.disabled = false;
                saveBtn.textContent = "保存变更";
            }
        }
    });
    $("#contactHeadActions").addEventListener("change", (event) => {
        const select = event.target.closest("select");
        if (!select) return;
        if (select.id === "operatorStatusSelect" || select.id === "indexLevelSelect" || select.id === "autoReplySelect") {
            updateSaveButtonState();
        }
    });
    $("#reloadAiTrainingQaBtn")?.addEventListener("click", () => {
        loadAiTrainingQa().catch((error) => showStatus(error.message, "error"));
    });
    $("#reloadAiTrainingDialoguesBtn")?.addEventListener("click", () => {
        loadAiTrainingDialogues().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingAddQaBtn")?.addEventListener("click", () => showQaEditModal());
    $("#aiTrainingQaForm")?.addEventListener("submit", (event) => {
        saveAiTrainingQaItem(event).catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingQaCancelBtn")?.addEventListener("click", hideAiTrainingQaModal);
    $("#aiTrainingQaModalCloseBtn")?.addEventListener("click", hideAiTrainingQaModal);
    $("#aiTrainingQaModalBackdrop")?.addEventListener("click", hideAiTrainingQaModal);
    $("#aiTrainingQaTable")?.addEventListener("click", (event) => {
        const button = event.target.closest("[data-action]");
        if (!button) return;
        const qaId = Number(button.dataset.qaId);
        const item = state.aiTraining.qaItems.find((row) => row.id === qaId);
        if (button.dataset.action === "edit-ai-training-qa" && item) {
            showQaEditModal(item);
            return;
        }
        if (button.dataset.action === "delete-ai-training-qa" && qaId) {
            deleteQaItem(qaId).catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#aiTrainingSourceFilter")?.addEventListener("change", (event) => {
        state.aiTraining.qaSource = event.target.value;
        state.aiTraining.qaPage = 0;
        loadAiTrainingQa().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingQaPrevPage")?.addEventListener("click", () => {
        if (state.aiTraining.qaPage > 0) {
            state.aiTraining.qaPage -= 1;
            loadAiTrainingQa().catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#aiTrainingQaNextPage")?.addEventListener("click", () => {
        state.aiTraining.qaPage += 1;
        loadAiTrainingQa().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingPromptForm")?.addEventListener("submit", (event) => {
        saveAiTrainingPromptConfig(event).catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingRestoreDefaultBtn")?.addEventListener("click", () => {
        restoreAiTrainingPromptDefault().catch((error) => showStatus(error.message, "error"));
    });
    document.querySelectorAll("#view-ai-training .ai-tab").forEach((button) => {
        button.addEventListener("click", () => switchAiTrainingTab(button.dataset.tab));
    });
    $("#aiTrainingExpertTagFilters")?.addEventListener("click", (event) => {
        const chip = event.target.closest(".ai-training-tag-chip");
        if (!chip) return;
        state.aiTraining.selectedExpertTag = chip.dataset.value || "";
        state.aiTraining.simulateMailsPage = 0;
        renderAiTrainingTagPills(
            "#aiTrainingExpertTagFilters",
            state.aiTraining.expertTagOptions,
            state.aiTraining.selectedExpertTag,
            "tag"
        );
        loadAiTrainingSimulateMails().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingInboundTagFilters")?.addEventListener("click", (event) => {
        const chip = event.target.closest(".ai-training-tag-chip");
        if (!chip) return;
        state.aiTraining.selectedInboundTagKey = chip.dataset.value || "";
        state.aiTraining.simulateMailsPage = 0;
        renderAiTrainingTagPills(
            "#aiTrainingInboundTagFilters",
            state.aiTraining.inboundTagOptions,
            state.aiTraining.selectedInboundTagKey,
            "tagKey"
        );
        loadAiTrainingSimulateMails().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiSimulateMailList")?.addEventListener("click", (event) => {
        const item = event.target.closest(".ai-training-mail-item");
        if (!item) return;
        const contactId = Number(item.dataset.contactId);
        const mail = (state.aiTraining.simulateMails || []).find((row) => row.expertContactId === contactId);
        if (mail) {
            selectSimulateMail(mail);
        }
    });
    $("#aiSimulateMailPrevPage")?.addEventListener("click", () => {
        if (state.aiTraining.simulateMailsPage > 0) {
            state.aiTraining.simulateMailsPage -= 1;
            loadAiTrainingSimulateMails().catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#aiSimulateMailNextPage")?.addEventListener("click", () => {
        state.aiTraining.simulateMailsPage += 1;
        loadAiTrainingSimulateMails().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingSimulateBtn")?.addEventListener("click", () => {
        runAiTrainingSimulate().catch((error) => showStatus(error.message, "error"));
    });
    $("#aiTrainingReplyModel")?.addEventListener("change", (event) => {
        state.aiTraining.simulateModel = readAiReplyModelSelection("#aiTrainingReplyModel", event.target.value);
    });
    document.addEventListener("change", (event) => {
        if (event.target?.id === "trustReplyModel") {
            aiReplyState.selectedModel = readAiReplyModelSelection("#trustReplyModel", event.target.value);
        }
        if (event.target?.id === "aiMailboxReplyModel") {
            aiReplyState.selectedModel = readAiReplyModelSelection("#aiMailboxReplyModel", event.target.value);
        }
        if (event.target?.id === "trustReplyAttemptTimeout") {
            aiReplyState.attemptTimeoutMode = event.target.value;
            syncAiReplyTimeoutControls();
        }
        if (event.target?.id === "trustReplyTotalTimeout") {
            aiReplyState.totalTimeoutMode = event.target.value;
            syncAiReplyTimeoutControls();
        }
    });
    document.addEventListener("input", (event) => {
        if (event.target?.id === "trustReplyAttemptTimeoutCustom") {
            aiReplyState.attemptCustomSeconds = event.target.value;
            syncAiReplyTimeoutControls();
        }
        if (event.target?.id === "trustReplyTotalTimeoutCustom") {
            aiReplyState.totalCustomSeconds = event.target.value;
            syncAiReplyTimeoutControls();
        }
    });
    $("#aiTrainingSimulateMessages")?.addEventListener("click", async (event) => {
        const btn = event.target.closest("[data-action='copy-ai-draft']");
        if (!btn) return;
        const sim = state.aiTraining.simulateResult;
        const text = sim?.renderedDraftText || sim?.draftText || "";
        try {
            await navigator.clipboard.writeText(text);
            showStatus("草稿已复制到剪贴板", "ok");
        } catch {
            showStatus("复制失败，请手动选择文本复制", "error");
        }
    });
    $("#loadTasksBtn").addEventListener("click", loadTasks);
    document.addEventListener("submit", (event) => {
        const form = event.target.closest("#meetingScheduleForm");
        if (form) {
            event.preventDefault();
            confirmMeetingSchedule(form).catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#loadUnmatchedBtn")?.addEventListener("click", refreshUnmatchedBadge);
    $("#unmatchedDetailPanel").addEventListener("click", (event) => {
        const button = event.target.closest("[data-action]");
        if (!button) return;
        if (button.dataset.action === "expert-add-tag-open" || button.dataset.action === "expert-remove-tag") {
            handleContactAction(button).catch((error) => showStatus(error.message, "error"));
            return;
        }
        handleUnmatchedAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#unmatchedDetailPanel").addEventListener("input", (event) => {
        if (event.target.id === "manualRichReplyEditor") {
            schedulePreflightCheck();
        }
    });
    $("#closeUnmatchedDetailBtn")?.addEventListener("click", () => {
        $("#unmatchedDetailPanel").hidden = true;
        state.mailbox.detailContext = null;
    });
    const updateFilterBadge = () => {
        const active = [
            $("#expertSortBy").value !== "",
            $("#expertIndexLevel").value !== "CANDIDATE",
            $("#expertIndexSize").value !== "50",
            $("#contactStatusFilter").value !== "",
            $("#contactNeedsAttentionFilter").value !== "",
            $("#contactReplyModeFilter").value !== "",
            $("#expertTagFilter").value !== "",
            $("#expertEmailDomainFilter")?.value !== "",
            $("#expertRegionFilter")?.value !== "",
            $("#expertDisciplineFilter")?.value !== "",
            ($("#expertHIndexMinFilter")?.value || "") !== "",
            ($("#expertCitationMinFilter")?.value || "") !== "",
            ($("#expertRecentYearsFilter")?.value || "") !== "",
            ($("#expertHasFieldFilter")?.selectedOptions?.length || 0) > 0
        ].filter(Boolean).length;
        const countEl = $("#filterActiveCount");
        countEl.hidden = active === 0;
        countEl.textContent = active;
    };
    const reloadContactsFromStart = () => {
        state.contactsPage = 0;
        updateFilterBadge();
        loadContacts().catch((e) => showStatus(e.message, "error"));
    };
    ["expertIndexLevel", "expertIndexSize", "contactNeedsAttentionFilter", "contactReplyModeFilter",
        "contactStatusFilter", "expertTagFilter", "expertSortBy", "expertEmailDomainFilter",
        "expertRegionFilter", "expertDisciplineFilter", "expertHIndexMinFilter", "expertCitationMinFilter",
        "expertRecentYearsFilter"].forEach((id) => {
        $(`#${id}`).addEventListener("change", reloadContactsFromStart);
    });
    ["expertHIndexMinFilter", "expertCitationMinFilter"].forEach((id) => {
        const el = $(`#${id}`);
        if (el) {
            el.addEventListener("keydown", (e) => {
                if (e.key === "Enter") reloadContactsFromStart();
            });
        }
    });
    updateFilterBadge();
    $("#filterToggleBtn").addEventListener("click", () => {
        const group = $("#contactsFilterGroup");
        const open = group.classList.toggle("open");
        $("#filterToggleBtn").setAttribute("aria-expanded", String(open));
    });

    /* ── Tag-chip multi-select for 数据完整度 ── */
    (function initHasFieldTags() {
        const container = $("#hasFieldTagSelect");
        if (!container) return;
        const chips = container.querySelectorAll(".tag-chip");

        /* Shim: expose .selectedOptions so existing query code works unchanged */
        const shimEl = document.createElement("span");
        shimEl.id = "expertHasFieldFilter";
        shimEl.style.display = "none";
        Object.defineProperty(shimEl, "selectedOptions", {
            get() {
                return Array.from(chips)
                    .filter((c) => c.classList.contains("active"))
                    .map((c) => ({ value: c.dataset.value }));
            }
        });
        container.appendChild(shimEl);

        chips.forEach((chip) => chip.addEventListener("click", () => {
            chip.classList.toggle("active");
            reloadContactsFromStart();
        }));
    })();
    $("#contactPrevPage").addEventListener("click", () => {
        if (state.contactsPage > 0) {
            state.contactsPage -= 1;
            loadContacts().catch((e) => showStatus(e.message, "error"));
        }
    });
    $("#contactNextPage").addEventListener("click", () => {
        state.contactsPage += 1;
        loadContacts().catch((e) => showStatus(e.message, "error"));
    });

    // 发现专家 split button 下拉菜单
    const discoverModeToggle = $("#discoverModeToggle");
    const discoverModeMenu = $("#discoverModeMenu");
    if (discoverModeToggle && discoverModeMenu) {
        discoverModeToggle.addEventListener("click", (event) => {
            event.stopPropagation();
            discoverModeMenu.hidden = !discoverModeMenu.hidden;
            discoverModeToggle.setAttribute("aria-expanded", String(!discoverModeMenu.hidden));
        });
        document.addEventListener("click", (event) => {
            if (!discoverModeMenu.hidden && !event.target.closest("#discoverBtnGroup")) {
                discoverModeMenu.hidden = true;
                discoverModeToggle.setAttribute("aria-expanded", "false");
            }
        });
        discoverModeMenu.addEventListener("click", (event) => {
            if (event.target.closest(".dropdown-item")) {
                discoverModeMenu.hidden = true;
                discoverModeToggle.setAttribute("aria-expanded", "false");
            }
        });
    }

    document.addEventListener("click", async (event) => {
        const element = event.target.closest("[data-action]");
        if (!element) return;
        const action = element.dataset.action;
        if (action === "goto-manual-queue") {
            event.preventDefault();
            setView("mailbox");
            setMailboxPendingOnly(true);
            state.mailbox.page = 0;
            loadMailbox().catch((e) => showStatus(e.message, "error"));
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

    $("#contactStatusFilter").innerHTML = optionsFromArray(operatorStatusOptions, true, "全部状态");

    // 旧进度条点击打开弹窗
    $("#taskProgressBar")?.addEventListener("click", async () => {
        if (currentTaskModal) return;
        for (const [taskType, mapping] of Object.entries(taskButtonMapping)) {
            if (taskType === "MANUAL_INITIAL_OUTREACH") continue;
            try {
                const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
                await handleAuthResponse(response);
                if (!response.ok) continue;
                const progress = await response.json();
                if (progress.status === "RUNNING" || progress.status === "CANCELLING" || isProgressTerminal(progress.status)) {
                    openTaskModal(taskType, mapping.label, mapping.btnId, {
                        knownActiveAtOpen: progress.status === "RUNNING" || progress.status === "CANCELLING"
                    });
                    break;
                }
            } catch (e) {}
        }
    });

    // 弹窗 ESC 关闭
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && currentTaskModal) {
            closeTaskModal();
        }
    });

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

let lastAutoReplySummary = null;


async function refreshAutoReplySummary() {
    const btn = $("#bulkAutoReplyBtn");
    if (!btn) return;
    try {
        const summary = await api("/api/expert-contacts/auto-reply/summary");
        lastAutoReplySummary = summary;
        if (summary.globalEnabled === false) {
            btn.textContent = "自动回复：全部关闭";
        } else {
            btn.textContent = "自动回复：全部开启 ✓";
        }
        btn.disabled = false;
    } catch (e) {
        btn.textContent = "自动回复：加载失败";
        console.error("加载自动回复汇总失败", e);
    }
}

function initBulkAutoReply() {
    const btn = $("#bulkAutoReplyBtn");
    if (!btn) return;

    btn.addEventListener("click", async () => {
        if (!lastAutoReplySummary) return;
        const targetEnabled = !lastAutoReplySummary.globalEnabled;
        const confirmMsg = targetEnabled
            ? "是否确认开启全局自动回复？"
            : "是否确认关闭全局自动回复？此后所有专家都不会收到自动回复邮件。";

        const confirmed = confirm(confirmMsg);
        if (!confirmed) return;

        const operatorName = $("#currentUserDisplay")?.textContent?.trim() || "console";

        btn.disabled = true;
        btn.textContent = "正在更新...";

        try {
            const res = await api("/api/expert-contacts/auto-reply/bulk", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ enabled: targetEnabled, operatorName })
            });
            showStatus(`全局自动回复已${res.globalEnabled ? "开启" : "关闭"}`, "ok");
        } catch (e) {
            showStatus("更新失败: " + e.message, "error");
        } finally {
            await refreshAutoReplySummary();
            await loadContacts();
        }
    });

    $("#mailboxRefreshBtn").addEventListener("click", () => {
        loadMailbox().catch((e) => showStatus(e.message, "error"));
    });
    bindInboundSummaryEvents();
    $("#mailboxSearchBtn").addEventListener("click", () => {
        state.mailbox.page = 0;
        loadMailbox().catch((e) => showStatus(e.message, "error"));
    });
    $("#mailboxPagination").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        if (button.dataset.action === "mailbox-prev") state.mailbox.page = Math.max(0, state.mailbox.page - 1);
        if (button.dataset.action === "mailbox-next") state.mailbox.page += 1;
        if (button.dataset.action === "mailbox-page") state.mailbox.page = Number(button.dataset.page);
        loadMailbox().catch((e) => showStatus(e.message, "error"));
    });
    $("#mailboxFilterRecipient").addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            state.mailbox.page = 0;
            loadMailbox().catch((e) => showStatus(e.message, "error"));
        }
    });
    $("#mailboxFilterKeyword").addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            state.mailbox.page = 0;
            loadMailbox().catch((e) => showStatus(e.message, "error"));
        }
    });
    $("#mailboxFilterAccountCode").addEventListener("change", () => {
        state.mailbox.page = 0;
        loadMailbox().catch((e) => showStatus(e.message, "error"));
    });
    $("#mailboxFilterDirection").addEventListener("change", () => {
        state.mailbox.page = 0;
        loadMailbox().catch((e) => showStatus(e.message, "error"));
    });
    $("#mailboxFilterTag").addEventListener("change", (event) => {
        const value = event.target.value;
        state.mailbox.tagFilter = value;
        if (value === "待处理") {
            setMailboxPendingOnly(true);
            state.mailbox.page = 0;
            loadMailbox().catch((e) => showStatus(e.message, "error"));
            return;
        }
        if (mailboxViewMode() === "EXPERT") {
            state.mailbox.page = 0;
            loadMailbox().catch((e) => showStatus(e.message, "error"));
            return;
        }
        renderMailboxTable();
    });
    document.querySelectorAll('input[name="mailboxMailScope"]').forEach((input) => {
        input.addEventListener("change", () => {
            state.mailbox.onlyPending = mailboxPendingOnly();
            state.mailbox.page = 0;
            if (!state.mailbox.onlyPending && state.mailbox.tagFilter === "待处理") {
                state.mailbox.tagFilter = "";
                $("#mailboxFilterTag").value = "";
            }
            loadMailbox().catch((e) => showStatus(e.message, "error"));
        });
    });
    document.querySelectorAll('input[name="mailboxViewMode"]').forEach((input) => {
        input.addEventListener("change", () => {
            state.mailbox.page = 0;
            syncMailboxViewModeControls();
            loadMailbox().catch((e) => showStatus(e.message, "error"));
        });
    });
    $("#mailboxList").addEventListener("click", async (event) => {
        const target = event.target.closest("[data-action]");
        if (!target) return;
        if (target.dataset.action === "open-monitoring-contact") {
            await openContactInList(Number(target.dataset.id));
            return;
        }
        if (target.dataset.action === "view-mail") {
            await showMailDetail(target.dataset.source, target.dataset.id);
            return;
        }
        if (["open-pending", "mark-unmatched-resolved", "view-unmatched", "open-contact-from-unmatched"].includes(target.dataset.action)) {
            await handleUnmatchedAction(target);
        }
    });
}



function summarizeManualOutreachPending(countRes) {
    const pending = Number(countRes?.pending || 0);
    const retryable = Number(countRes?.retryable || 0);
    const totalSendable = Number(countRes?.totalSendable ?? (pending + retryable));
    const missingNote = retryable > 0
        ? `（含 ${retryable} 位上次失败待补发）`
        : "";
    return {
        pending,
        retryable,
        totalSendable,
        total: totalSendable,
        confirmMessage: `将向 ${totalSendable} 位专家发送介绍邮件（${pending} 位未联系）${missingNote}，是否开始？`,
        emptyMessage: totalSendable > 0
            ? ""
            : "没有待发送的专家（没有满足条件的未联系候选人且无上次失败的专家）"
    };
}

async function refreshOutreachPendingCount() {
    const descEl = $("#taskLaunchDesc");
    const runBtn = $("#taskLaunchRunBtn");
    if (!descEl) return;
    try {
        const countRes = await api("/api/mail/manual-outreach/pending-count");
        const summary = summarizeManualOutreachPending(countRes);
        descEl.textContent = summary.total > 0 ? summary.confirmMessage : summary.emptyMessage;
        if (runBtn) runBtn.disabled = summary.total <= 0;
    } catch (e) {
        descEl.textContent = "加载待发送专家数失败: " + e.message;
        if (runBtn) runBtn.disabled = true;
    }
}



function initPollLogPanel() {
    const tbody = $("#pollLogBody");
    if (tbody) {
        tbody.addEventListener("click", (e) => {
            const row = e.target.closest("tr.poll-log-row");
            if (row) togglePollDetail(row).catch(err => showStatus(err.message, "error"));
        });
    }
    $("#closePollLogPanelBtn")?.addEventListener("click", () => {
        $("#pollLogPanel").hidden = true;
    });
}

function initLayoutResizer() {
    const resizer = document.getElementById("contactsLayoutResizer");
    const container = document.querySelector(".contacts-layout");
    const listPanel = document.querySelector(".contacts-list-panel");


    if (!resizer || !container || !listPanel) return;

    let isDragging = false;

    // Load saved layout width or default
    const savedWidth = localStorage.getItem("contacts-list-width");

    function setListWidth(width, updateStorage = true) {
        // Ensure within reasonable boundaries: min 200px, max 60% window width
        const maxWidth = Math.min(800, window.innerWidth * 0.6);
        const targetWidth = Math.max(200, Math.min(maxWidth, width));

        container.style.gridTemplateColumns = `${targetWidth}px 6px minmax(0, 1fr)`;
        listPanel.style.display = "";
        resizer.style.display = "";

        if (updateStorage) {
            localStorage.setItem("contacts-list-width", targetWidth);
        }
    }

    function resetToDefault() {
        setListWidth(260);
    }

    // Double-click resizer to reset
    resizer.addEventListener("dblclick", resetToDefault);

    // Pointer events: 同时支持鼠标和触屏拖拽
    resizer.addEventListener("pointerdown", (e) => {
        isDragging = true;
        resizer.setPointerCapture(e.pointerId);
        resizer.classList.add("dragging");
        document.body.style.cursor = "col-resize";
        document.body.style.userSelect = "none";
    });

    resizer.addEventListener("pointermove", (e) => {
        if (!isDragging) return;
        const containerRect = container.getBoundingClientRect();
        const newWidth = e.clientX - containerRect.left;
        setListWidth(newWidth);
    });

    const endDrag = (e) => {
        if (isDragging) {
            isDragging = false;
            if (resizer.hasPointerCapture?.(e.pointerId)) {
                resizer.releasePointerCapture(e.pointerId);
            }
            resizer.classList.remove("dragging");
            document.body.style.cursor = "";
            document.body.style.userSelect = "";
        }
    };
    resizer.addEventListener("pointerup", endDrag);
    resizer.addEventListener("pointercancel", endDrag);

    // Preset buttons
    document.getElementById("btnLayoutDefault")?.addEventListener("click", resetToDefault);
    document.getElementById("btnLayoutWideList")?.addEventListener("click", () => setListWidth(500));
    document.getElementById("btnLayoutSplit")?.addEventListener("click", () => {
        const containerWidth = container.getBoundingClientRect().width;
        setListWidth(Math.floor(containerWidth / 2) - 3);
    });

    // Initialize state
    if (savedWidth) {
        setListWidth(parseInt(savedWidth), false);
    } else {
        resetToDefault();
    }
}

let appStarted = false;

function startAuthenticatedApp(username) {
    $("#loginOverlay").hidden = true;
    $("#changePasswordOverlay").hidden = true;
    const userDisplay = $("#currentUserDisplay");
    if (userDisplay) {
        userDisplay.textContent = username;
    }
    const shell = $(".app-shell");
    if (shell) {
        shell.style.display = "grid";
    }
    if (!appStarted) {
        appStarted = true;
        if (typeof ensureVariableMeta === "function") {
            ensureVariableMeta()
                .then(() => {
                    if (typeof refreshVariableEditors === "function") {
                        return refreshVariableEditors();
                    }
                    return undefined;
                })
                .catch((error) => showStatus(error.message, "error"));
        }
        updateUnmatchedBadge();
        resumeProgressPollingIfNeeded().catch(() => {});
        initBatchSendBanner();
        refreshCurrentView();
    }
}

function stopAuthenticatedApp() {
    appStarted = false;
    const shell = $(".app-shell");
    if (shell) {
        shell.style.display = "none";
    }
    stopBatchSendBannerPoll();
    stopBatchSendStatusPoll();
    stopAllWatchers();
    if (currentTaskModal) {
        stopTaskModalPolling();
        $("#taskProgressModal").hidden = true;
        document.body.classList.remove("modal-open");
        currentTaskModal = null;
    }
    if (state.monitoring.autoRefreshTimer) {
        clearTimeout(state.monitoring.autoRefreshTimer);
        state.monitoring.autoRefreshTimer = null;
    }
}

window.stopAuthenticatedApp = stopAuthenticatedApp;

function stopAllWatchers() {
    if (typeof taskWatchers === 'object' && taskWatchers) {
        for (const taskType of Object.keys(taskWatchers)) {
            stopTaskWatcher(taskType, true);
        }
    }
}

async function checkAuth() {
    try {
        const res = await api("/api/auth/me");
        if (res.authenticated) {
            if (res.mustChangePassword) {
                stopAuthenticatedApp();
                $("#changePasswordOverlay").hidden = false;
                $("#loginOverlay").hidden = true;
            } else {
                startAuthenticatedApp(res.username);
            }
        } else {
            stopAuthenticatedApp();
            $("#loginOverlay").hidden = false;
            $("#changePasswordOverlay").hidden = true;
        }
    } catch (e) {
        stopAuthenticatedApp();
        $("#loginOverlay").hidden = false;
        $("#loginError").textContent = "无法连接至服务器: " + e.message;
        $("#loginError").hidden = false;
    }
}

function bindAuthEvents() {
    $("#loginForm")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        const username = $("#loginUsername").value.trim();
        const password = $("#loginPassword").value;
        const errDiv = $("#loginError");
        errDiv.hidden = true;

        if (!username || !password) {
            errDiv.textContent = "用户名和密码不能为空";
            errDiv.hidden = false;
            return;
        }

        try {
            const res = await api("/api/auth/login", {
                method: "POST",
                body: JSON.stringify({ username, password })
            });
            if (res.mustChangePassword) {
                stopAuthenticatedApp();
                $("#loginOverlay").hidden = true;
                $("#changePasswordOverlay").hidden = false;
                $("#changePasswordError").hidden = true;
            } else {
                startAuthenticatedApp(res.username);
            }
        } catch (err) {
            errDiv.textContent = err.message || "登录失败，请检查用户名或密码";
            errDiv.hidden = false;
        }
    });

    $("#changePasswordForm")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        const oldPassword = $("#oldPassword").value;
        const newPassword = $("#newPassword").value;
        const confirmPassword = $("#confirmNewPassword").value;
        const errDiv = $("#changePasswordError");
        errDiv.hidden = true;

        if (!oldPassword || !newPassword || !confirmPassword) {
            errDiv.textContent = "密码字段不能为空";
            errDiv.hidden = false;
            return;
        }

        if (newPassword.length < 8) {
            errDiv.textContent = "新密码长度不能少于8位";
            errDiv.hidden = false;
            return;
        }

        if (newPassword !== confirmPassword) {
            errDiv.textContent = "两次输入的新密码不一致";
            errDiv.hidden = false;
            return;
        }

        try {
            await api("/api/auth/change-password", {
                method: "POST",
                body: JSON.stringify({ oldPassword, newPassword })
            });
            showStatus("密码修改成功");
            await checkAuth();
        } catch (err) {
            errDiv.textContent = err.message || "密码修改失败";
            errDiv.hidden = false;
        }
    });

    $("#logoutBtn")?.addEventListener("click", async () => {
        try {
            await api("/api/auth/logout", { method: "POST" });
        } catch (e) {
        } finally {
            location.reload();
        }
    });
}

function mailboxViewMode() {
    const checked = document.querySelector('input[name="mailboxViewMode"]:checked');
    return checked?.value === "EXPERT" ? "EXPERT" : "MAIL";
}

function mailboxPendingOnly() {
    return document.querySelector('input[name="mailboxMailScope"]:checked')?.value === "PENDING";
}

function setMailboxPendingOnly(pending) {
    const value = pending ? "PENDING" : "ALL";
    const input = document.querySelector(`input[name="mailboxMailScope"][value="${value}"]`);
    if (input) input.checked = true;
    state.mailbox.onlyPending = pending;
}

function syncMailboxViewModeControls() {
    const expertMode = mailboxViewMode() === "EXPERT";
    state.mailbox.viewMode = expertMode ? "EXPERT" : "MAIL";
}

async function loadMailboxAccounts() {
    if (state.mailbox.accountsLoaded) return;
    try {
        const accounts = await api("/api/mail/sender-accounts");
        const activeAccounts = accounts.filter(a => a.enabled);
        const select = $("#mailboxFilterAccountCode");
        select.innerHTML = '<option value="">全部邮箱账号</option>' + activeAccounts.map(a =>
            `<option value="${escapeHtml(a.accountCode)}">${escapeHtml(a.accountCode)} (${escapeHtml(a.senderEmail)})</option>`
        ).join("");
        state.mailbox.accountsLoaded = true;
    } catch (e) {
        showStatus("加载邮箱账号失败: " + e.message, "error");
    }
}

async function loadMailbox() {
    await loadMailboxAccounts();
    syncMailboxViewModeControls();

    const expertMode = state.mailbox.viewMode === "EXPERT";
    state.mailbox.onlyPending = mailboxPendingOnly();
    state.mailbox.tagFilter = $("#mailboxFilterTag")?.value || "";

    const startInput = $("#mailboxFilterStartDate");
    const endInput = $("#mailboxFilterEndDate");
    const disabled = state.mailbox.onlyPending;
    startInput.disabled = disabled;
    endInput.disabled = disabled;
    startInput.classList.toggle("input-disabled", disabled);
    endInput.classList.toggle("input-disabled", disabled);

    if (!state.mailbox.onlyPending) {
        if (!state.mailbox.dateDefaultsApplied && !startInput.value && !endInput.value) {
            const weekAgo = new Date();
            weekAgo.setDate(weekAgo.getDate() - 7);
            endInput.value = monitoringToday();
            startInput.value = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(weekAgo);
        }
        state.mailbox.dateDefaultsApplied = true;
    }

    const params = new URLSearchParams();
    const accountCode = $("#mailboxFilterAccountCode").value;
    const recipientEmail = $("#mailboxFilterRecipient").value.trim();
    const keyword = $("#mailboxFilterKeyword").value.trim();

    if (accountCode) params.set("accountCode", accountCode);
    if (recipientEmail) params.set("recipientEmail", recipientEmail);
    if (keyword) params.set("keyword", keyword);

    const direction = $("#mailboxFilterDirection").value;
    const startDate = startInput.value;
    const endDate = endInput.value;
    if (direction) params.set("direction", direction);
    if (!state.mailbox.onlyPending) {
        if (startDate) params.set("startDate", startDate);
        if (endDate) params.set("endDate", endDate);
    }
    if (state.mailbox.onlyPending) params.set("pending", "true");
    if (expertMode && state.mailbox.tagFilter) params.set("tag", state.mailbox.tagFilter);

    params.set("page", state.mailbox.page);
    params.set("size", state.mailbox.pageSize);

    try {
        if (expertMode) {
            const data = await api(`/api/mail/mailbox/by-expert?${params}`);
            state.mailbox.groups = data.groups || [];
            state.mailbox.items = [];
            state.mailbox.totalCount = data.totalCount || 0;
            renderMailboxExpertGroups();
        } else {
            const data = await api(`/api/mail/mailbox?${params}`);
            state.mailbox.items = data.items || [];
            state.mailbox.groups = [];
            state.mailbox.totalCount = data.totalCount || 0;
            renderMailboxTable();
        }
        renderMailboxPagination();
        await refreshUnmatchedBadge();
    } catch (e) {
        showStatus("获取邮件记录失败: " + e.message, "error");
    }
}

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

        return `
            <details class="inbound-expert-group">
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
}

function renderMailboxPagination() {
    const total = state.mailbox.totalCount || 0;
    const page = state.mailbox.page;
    const maxPage = Math.max(0, Math.ceil(total / state.mailbox.pageSize) - 1);
    const unitLabel = state.mailbox.viewMode === "EXPERT" ? "位专家" : "条";

    // 生成页码窗口：当前页两侧各 2 页，首尾用省略号补齐
    const pages = [];
    const push = (p) => { if (!pages.includes(p) && p >= 0 && p <= maxPage) pages.push(p); };
    push(0);
    for (let p = page - 2; p <= page + 2; p++) push(p);
    push(maxPage);
    pages.sort((a, b) => a - b);

    let pageBtns = "";
    let prev = -1;
    for (const p of pages) {
        if (prev !== -1 && p - prev > 1) pageBtns += `<span class="page-ellipsis">…</span>`;
        pageBtns += `<button class="button secondary page-num${p === page ? " active" : ""}" data-action="mailbox-page" data-page="${p}" ${p === page ? "disabled" : ""}>${p + 1}</button>`;
        prev = p;
    }

    $("#mailboxPagination").innerHTML = `
        <span class="muted">共 ${escapeHtml(total)} ${unitLabel}，第 ${escapeHtml(page + 1)} / ${escapeHtml(maxPage + 1)} 页</span>
        <button class="button secondary" data-action="mailbox-prev" ${page <= 0 ? "disabled" : ""}>上一页</button>
        ${pageBtns}
        <button class="button secondary" data-action="mailbox-next" ${page >= maxPage ? "disabled" : ""}>下一页</button>
    `;
}

const INBOUND_TAG_CHART_COLORS = [
    "#3b82f6", "#8b5cf6", "#06b6d4", "#10b981", "#f59e0b", "#ef4444", "#6366f1", "#ec4899",
    "#14b8a6", "#f97316", "#a855f7", "#0ea5e9"
];

function defaultInboundFromDate() {
    const d = new Date();
    d.setDate(d.getDate() - 90);
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(d);
}

function inboundSummaryExclusiveToDate(dateStr) {
    if (!dateStr) return "";
    const parts = dateStr.split("-").map(Number);
    const d = new Date(parts[0], parts[1] - 1, parts[2] + 1);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
}

function initInboundSummaryDatesIfNeeded() {
    if (state.inboundSummary.datesInitialized) return;
    state.inboundSummary.from = defaultInboundFromDate();
    state.inboundSummary.to = monitoringToday();
    state.inboundSummary.datesInitialized = true;
    const fromEl = $("#inboundFrom");
    const toEl = $("#inboundTo");
    if (fromEl) fromEl.value = state.inboundSummary.from;
    if (toEl) toEl.value = state.inboundSummary.to;
}

function syncInboundSummaryDatesFromInputs() {
    const fromEl = $("#inboundFrom");
    const toEl = $("#inboundTo");
    if (fromEl?.value) state.inboundSummary.from = fromEl.value;
    if (toEl?.value) state.inboundSummary.to = toEl.value;
}

function inboundSummaryDateParams() {
    syncInboundSummaryDatesFromInputs();
    const params = new URLSearchParams();
    params.set("from", `${state.inboundSummary.from}T00:00:00`);
    params.set("to", `${inboundSummaryExclusiveToDate(state.inboundSummary.to)}T00:00:00`);
    return params;
}

function inboundSummaryOperatorName() {
    const name = ($("#currentUserDisplay")?.textContent || "").trim();
    return name || null;
}

function renderInboundTagChip(tag, options = {}) {
    const {
        filterKey = null,
        removable = false,
        clickable = false,
        extraClass = "",
        removeAction = "inbound-remove-tag"
    } = options;
    const inactive = tag.active === false;
    const classes = ["inbound-tag-chip", tag.tagType === "QA" ? "qa" : "custom"];
    if (inactive) classes.push("inactive");
    if (clickable) classes.push("filter-chip");
    if (clickable && filterKey && state.inboundSummary.activeTagKey === filterKey) classes.push("selected");
    if (extraClass) classes.push(extraClass);
    const attrs = [];
    if (filterKey) attrs.push(`data-tag-key="${escapeHtml(filterKey)}"`);
    if (tag.tagId != null) attrs.push(`data-tag-id="${escapeHtml(tag.tagId)}"`);
    const removeBtn = removable
        ? `<button type="button" class="chip-x" data-action="${escapeHtml(removeAction)}" data-tag-id="${escapeHtml(tag.tagId)}" title="删除标签">×</button>`
        : "";
    return `<span class="${classes.join(" ")}" ${attrs.join(" ")}>${escapeHtml(tag.label || "")}${removeBtn}</span>`;
}

function renderInboundStatChip(item) {
    const tag = { label: item.label, tagType: item.tagType, active: item.active !== false };
    return renderInboundTagChip(tag, { filterKey: item.tagKey, clickable: true });
}

async function loadInboundSummary() {
    initInboundSummaryDatesIfNeeded();
    syncInboundSummaryDatesFromInputs();
    await Promise.all([
        reloadInboundStats(),
        loadInboundMails()
    ]);
    if (state.inboundSummary.selectedId) {
        await selectInboundMail(state.inboundSummary.selectedId, { preserveSelection: true });
    } else {
        renderInboundDetailEmpty();
    }
}

async function reloadInboundStats() {
    const params = inboundSummaryDateParams();
    const [stats, optionsResp] = await Promise.all([
        api(`/api/inbound-summary/tags/stats?${params}`),
        api(`/api/inbound-summary/tags/options?${params}`)
    ]);
    state.inboundSummary.stats = stats;
    state.inboundSummary.options = optionsResp.items || [];
    renderTagBarChart(stats);
    renderTagPieChart(stats);
    renderInboundTagFilters(state.inboundSummary.options);
}

async function loadInboundMails() {
    const params = inboundSummaryDateParams();
    params.set("pageSize", String(state.inboundSummary.pageSize));
    params.set("pageOffset", String(state.inboundSummary.page * state.inboundSummary.pageSize));
    if (state.inboundSummary.activeTagKey) {
        params.set("tagKey", state.inboundSummary.activeTagKey);
    }
    const data = await api(`/api/inbound-summary/mails?${params}`);
    state.inboundSummary.mails = data.records || [];
    state.inboundSummary.total = data.totalCount || 0;
    renderInboundMailList();
    renderInboundPagination();
}

function filteredInboundMails() {
    const q = (state.inboundSummary.search || "").trim().toLowerCase();
    if (!q) return state.inboundSummary.mails || [];
    return (state.inboundSummary.mails || []).filter((mail) => {
        const haystack = [
            mail.fromEmail,
            mail.subject,
            mail.expertName,
            mail.processStatus
        ].filter(Boolean).join(" ").toLowerCase();
        return haystack.includes(q);
    });
}

function renderInboundTagFilters(options) {
    const container = $("#inboundTagFilters");
    if (!container) return;
    const items = options || [];
    if (items.length === 0) {
        container.innerHTML = `<span class="muted">暂无标签过滤项</span>`;
        return;
    }
    const allChip = `<span class="inbound-tag-chip filter-chip ${state.inboundSummary.activeTagKey ? "" : "selected"}" data-tag-key="">全部</span>`;
    container.innerHTML = allChip + items.map((item) => renderInboundStatChip(item)).join("");
}

function renderInboundMailRow(mail, options = {}) {
    const { grouped = false } = options;
    const timeStr = mail.receivedAt ? String(mail.receivedAt).replace("T", " ").slice(0, 19) : "-";
    const selected = state.inboundSummary.selectedId === mail.inboundId ? " selected" : "";
    const tags = (mail.tags || []).map((tag) => renderInboundTagChip(tag)).join("");
    const groupedClass = grouped ? " inbound-mail-row-grouped" : "";
    return `
        <div class="inbound-mail-row${selected}${groupedClass}" data-action="select-inbound-mail" data-id="${escapeHtml(mail.inboundId)}">
            <div class="inbound-mail-row-main">
                <span class="inbound-mail-row-from">${escapeHtml(mail.fromEmail || "-")}</span>
                <span class="inbound-mail-row-time">${escapeHtml(timeStr)}</span>
            </div>
            <div class="inbound-mail-row-subject">${escapeHtml(mail.subject || "(无主题)")}</div>
            <div class="inbound-mail-row-meta">${escapeHtml(mail.expertName || "未关联专家")}</div>
            <div class="inbound-mail-row-tags">${tags || `<span class="muted">无标签</span>`}</div>
        </div>
    `;
}

function renderInboundMailList() {
    const list = $("#inboundMailList");
    if (!list) return;
    const rows = filteredInboundMails();
    if (rows.length === 0) {
        list.innerHTML = `<div class="detail-empty muted" style="padding: 24px;">暂无来信记录</div>`;
        return;
    }
    if (!state.inboundSummary.groupByExpert) {
        list.innerHTML = rows.map((mail) => renderInboundMailRow(mail)).join("");
        return;
    }
    const groups = new Map();
    rows.forEach((mail) => {
        const key = mail.expertContactId != null ? String(mail.expertContactId) : "unknown";
        if (!groups.has(key)) {
            groups.set(key, {
                expertContactId: mail.expertContactId,
                expertName: mail.expertName || "未关联专家",
                fromEmail: mail.fromEmail || "-",
                mails: []
            });
        }
        groups.get(key).mails.push(mail);
    });
    list.innerHTML = Array.from(groups.values()).map((group) => {
        const mailRows = group.mails.map((mail) => renderInboundMailRow(mail, { grouped: true })).join("");
        return `
            <details class="inbound-expert-group">
                <summary class="inbound-expert-group-header">
                    <span class="inbound-expert-group-name">${escapeHtml(group.expertName)}</span>
                    <span class="inbound-expert-group-email">${escapeHtml(group.fromEmail)}</span>
                    <span class="inbound-expert-group-count">${escapeHtml(group.mails.length)} 封来信</span>
                </summary>
                <div class="inbound-expert-group-mails">${mailRows}</div>
            </details>
        `;
    }).join("");
}

function renderInboundPagination() {
    const container = $("#inboundPagination");
    if (!container) return;
    const total = state.inboundSummary.total || 0;
    const page = state.inboundSummary.page;
    const maxPage = Math.max(0, Math.ceil(total / state.inboundSummary.pageSize) - 1);
    container.innerHTML = `
        <span class="muted">共 ${escapeHtml(total)} 条，第 ${escapeHtml(page + 1)} / ${escapeHtml(maxPage + 1)} 页</span>
        <button class="button secondary" data-action="inbound-prev" ${page <= 0 ? "disabled" : ""}>上一页</button>
        <button class="button secondary" data-action="inbound-next" ${page >= maxPage ? "disabled" : ""}>下一页</button>
    `;
}

function renderInboundDetailEmpty() {
    const editor = $("#inboundTagEditor");
    const thread = $("#inboundThread");
    if (editor) {
        editor.innerHTML = `<div class="detail-empty muted">请在左侧选择一封来信。</div>`;
    }
    if (thread) thread.innerHTML = "";
}

async function selectInboundMail(inboundId, options = {}) {
    state.inboundSummary.selectedId = inboundId;
    renderInboundMailList();
    const threadData = await api(`/api/inbound-summary/mails/${inboundId}/thread`);
    state.inboundSummary.thread = threadData;
    renderInboundThread(threadData);
}

function renderInboundTagEditor(threadData) {
    const editor = $("#inboundTagEditor");
    if (!editor) return;
    const tags = threadData.tags || [];
    const chips = tags.map((tag) => renderInboundTagChip(tag, { removable: true })).join("")
        || `<span class="muted">暂无标签</span>`;
    editor.innerHTML = `
        <div class="inbound-tag-editor-head">
            <h3>邮件标签</h3>
            <div class="inbound-tag-editor-actions">
                <button type="button" class="button secondary small" data-action="inbound-auto-tags">自动添加 QA 标签</button>
                <button type="button" class="button primary small" data-action="inbound-add-tag-open">+ 添加标签</button>
            </div>
        </div>
        <div class="inbound-tag-editor-chips">${chips}</div>
    `;
}

function renderMailboxInboundTagEditor(tags, inboundProcessingId) {
    const chips = (tags || []).map((tag) => renderInboundTagChip(tag, {
        removable: true,
        removeAction: "mailbox-remove-tag"
    })).join("") || `<span class="muted">暂无标签</span>`;
    return `
        <div class="detail-section" id="mailboxInboundTagEditor" data-inbound-id="${escapeHtml(inboundProcessingId)}">
            <div class="inbound-tag-editor-head">
                <h3>邮件标签</h3>
                <div class="inbound-tag-editor-actions">
                    <button type="button" class="button secondary small" data-action="mailbox-auto-tags">自动添加 QA 标签</button>
                    <button type="button" class="button primary small" data-action="mailbox-add-tag-open">+ 添加标签</button>
                </div>
            </div>
            <div class="inbound-tag-editor-chips">${chips}</div>
        </div>
    `;
}

function mailboxTagEditInboundId() {
    return state.mailbox.detailContext?.inboundProcessingId || null;
}

function showMailboxAddTagModal() {
    const inboundId = mailboxTagEditInboundId();
    if (!inboundId) return;
    state.mailbox.addTagInboundId = inboundId;
    showInboundAddTagModal();
}

async function refreshMailboxInboundTagsAfterChange() {
    const ctx = state.mailbox.detailContext;
    if (!ctx?.inboundProcessingId) return;
    const detail = await api(`/api/mail/mailbox/${encodeURIComponent(ctx.source)}/${ctx.id}`);
    const editor = $("#mailboxInboundTagEditor");
    if (editor) {
        editor.outerHTML = renderMailboxInboundTagEditor(detail.inboundTags || [], ctx.inboundProcessingId);
    }
}

async function mailboxAutoApplyTags(trigger = null) {
    const inboundId = mailboxTagEditInboundId();
    if (!inboundId) return;
    const editor = trigger?.closest("#mailboxInboundTagEditor") || $("#mailboxInboundTagEditor");
    const operatorName = inboundSummaryOperatorName();
    setTagEditorLoading(editor, true, "正在自动匹配 QA 标签...");
    try {
        const result = await api(`/api/inbound-summary/mails/${inboundId}/tags/auto`, {
            method: "POST",
            body: JSON.stringify(operatorName ? { operatorName } : {})
        });
        showAutoApplyTagStatus(result);
        await refreshMailboxInboundTagsAfterChange();
    } finally {
        setTagEditorLoading(editor, false);
    }
}

async function mailboxRemoveTag(tagId) {
    await api(`/api/inbound-summary/tags/${tagId}`, { method: "DELETE" });
    showStatus("标签已删除", "ok");
    await refreshMailboxInboundTagsAfterChange();
}

function renderInboundThreadBubbleTags(msg) {
    const isInbound = (msg.direction || "").toUpperCase() === "INBOUND";
    const inboundId = msg.inboundProcessingId;
    if (!isInbound || !inboundId) {
        return "";
    }
    const tags = (msg.tags || []).map((tag) => renderInboundTagChip(tag, { removable: true })).join("")
        || `<span class="muted">暂无标签</span>`;
    return `
        <div class="inbound-thread-bubble-tags">
            <div class="inbound-thread-bubble-tag-chips">${tags}</div>
            <div class="inbound-thread-bubble-tag-actions">
                <button type="button" class="button secondary small" data-action="inbound-auto-tags" data-inbound-id="${escapeHtml(inboundId)}">自动 QA 标签</button>
                <button type="button" class="button primary small" data-action="inbound-add-tag-open" data-inbound-id="${escapeHtml(inboundId)}">+ 添加标签</button>
            </div>
        </div>
    `;
}

function renderInboundThread(threadData) {
    const container = $("#inboundThread");
    if (!container) return;
    const messages = threadData.messages || [];
    if (messages.length === 0) {
        container.innerHTML = `<div class="detail-empty muted">暂无往来记录</div>`;
        return;
    }
    const currentMessageId = threadData.currentMessageId || null;
    container.innerHTML = messages.map((msg) => {
        const isInbound = (msg.direction || "").toUpperCase() === "INBOUND";
        const isCurrent = currentMessageId && msg.messageId && msg.messageId === currentMessageId;
        const classes = [
            "inbound-thread-bubble",
            isInbound ? "inbound" : "outbound",
            isCurrent ? "current" : ""
        ].filter(Boolean).join(" ");
        const timeStr = (msg.receivedAt || msg.sentAt || "")
            ? String(msg.receivedAt || msg.sentAt).replace("T", " ").slice(0, 19)
            : "-";
        const directionLabel = isInbound ? "来信" : "去信";
        const currentBadge = isCurrent ? `<span class="inbound-thread-current-badge">当前来信</span>` : "";
        return `
            <div class="${classes}">
                <div class="inbound-thread-bubble-subject">
                    ${escapeHtml(msg.subject || "(无主题)")}
                    ${currentBadge}
                </div>
                <div class="inbound-thread-bubble-meta">${escapeHtml(directionLabel)} · ${escapeHtml(timeStr)}</div>
                <div class="inbound-thread-bubble-body">${translatableBody(msg.body || "", { emptyLabel: "(无正文)" })}</div>
                ${renderInboundThreadBubbleTags(msg)}
            </div>
        `;
    }).join("");
}

function renderTagBarChart(stats) {
    const container = $("#inboundTagBarChart");
    if (!container) return;
    const items = stats?.items || [];
    if (items.length === 0) {
        container.innerHTML = `<div class="muted">暂无标签数据</div>`;
        return;
    }
    const maxCount = Math.max(...items.map((item) => Number(item.count) || 0), 1);
    container.innerHTML = items.slice(0, 12).map((item) => {
        const count = Number(item.count) || 0;
        const width = Math.max(2, Math.round((count / maxCount) * 100));
        const inactive = item.active === false;
        return `
            <div class="tag-bar-row">
                <span class="tag-bar-label ${inactive ? "inactive" : ""}">${escapeHtml(item.label || "")}</span>
                <div class="tag-bar-track"><div class="bar ${inactive ? "inactive" : ""}" style="width:${width}%"></div></div>
                <span class="tag-bar-count">${escapeHtml(count)}</span>
            </div>
        `;
    }).join("");
}

function renderTagPieChart(stats) {
    const container = $("#inboundTagPieChart");
    if (!container) return;
    const items = (stats?.items || []).slice(0, 8);
    const total = Number(stats?.total) || items.reduce((sum, item) => sum + (Number(item.count) || 0), 0) || 1;
    if (items.length === 0) {
        container.innerHTML = `<div class="muted">暂无标签数据</div>`;
        return;
    }
    const radius = 54;
    const circumference = 2 * Math.PI * radius;
    let offset = 0;
    const arcs = items.map((item, index) => {
        const count = Number(item.count) || 0;
        const length = (count / total) * circumference;
        const dash = `${length} ${circumference - length}`;
        const color = item.active === false ? "#94a3b8" : INBOUND_TAG_CHART_COLORS[index % INBOUND_TAG_CHART_COLORS.length];
        const arc = `<circle cx="80" cy="80" r="${radius}" fill="none" stroke="${color}" stroke-width="18"
            stroke-dasharray="${dash}" stroke-dashoffset="${-offset}"></circle>`;
        offset += length;
        return arc;
    }).join("");
    const legend = items.map((item, index) => {
        const count = Number(item.count) || 0;
        const pct = ((count / total) * 100).toFixed(1);
        const inactive = item.active === false;
        const color = inactive ? "#94a3b8" : INBOUND_TAG_CHART_COLORS[index % INBOUND_TAG_CHART_COLORS.length];
        return `
            <div class="tag-pie-legend-item ${inactive ? "inactive" : ""}">
                <span class="tag-pie-swatch" style="background:${color}"></span>
                <span class="tag-pie-legend-label">${escapeHtml(item.label || "")}</span>
                <span>${escapeHtml(count)}</span>
                <span class="muted">${escapeHtml(pct)}%</span>
            </div>
        `;
    }).join("");
    container.innerHTML = `
        <svg viewBox="0 0 160 160" aria-hidden="true">${arcs}</svg>
        <div class="tag-pie-legend">${legend}</div>
    `;
}

async function refreshInboundThreadAfterTagChange(inboundId) {
    const threadData = await api(`/api/inbound-summary/mails/${inboundId}/thread`);
    state.inboundSummary.thread = threadData;
    renderInboundThread(threadData);
    const currentMsg = (threadData.messages || []).find((msg) => Number(msg.inboundProcessingId) === Number(inboundId));
    const mail = (state.inboundSummary.mails || []).find((item) => Number(item.inboundId) === Number(inboundId));
    if (mail && currentMsg) {
        mail.tags = currentMsg.tags || [];
        renderInboundMailList();
    }
}

async function inboundAutoApplyTags(inboundId, trigger = null) {
    if (!inboundId) return;
    const editor = trigger?.closest(".inbound-thread-bubble-tags") || $("#inboundTagEditor");
    const operatorName = inboundSummaryOperatorName();
    setTagEditorLoading(editor, true, "正在自动匹配 QA 标签...");
    try {
        const result = await api(`/api/inbound-summary/mails/${inboundId}/tags/auto`, {
            method: "POST",
            body: JSON.stringify(operatorName ? { operatorName } : {})
        });
        showAutoApplyTagStatus(result);
        await refreshInboundThreadAfterTagChange(inboundId);
    } finally {
        setTagEditorLoading(editor, false);
    }
}

function showAutoApplyTagStatus(result) {
    if (result && result.addedCount === 0) {
        showStatus("未匹配到 QA 规则", "error");
        return;
    }
    if (result && typeof result.addedCount === "number") {
        showStatus(`已自动添加 ${result.addedCount} 个 QA 标签`, "ok");
        return;
    }
    showStatus("已自动添加 QA 标签", "ok");
}

async function inboundRemoveTag(tagId, inboundId) {
    await api(`/api/inbound-summary/tags/${tagId}`, { method: "DELETE" });
    showStatus("标签已删除", "ok");
    if (inboundId) {
        await refreshInboundThreadAfterTagChange(inboundId);
    }
}

function showInboundAddTagModal() {
    $("#inboundAddTagModal").hidden = false;
    document.body.classList.add("modal-open");
    $("#inboundAddTagType").value = "qa";
    $("#inboundAddTagCustomGroup").hidden = true;
    $("#inboundAddTagQaGroup").hidden = false;
    $("#inboundAddTagCustomLabel").value = "";
    populateInboundAddTagQaOptions().catch((error) => showStatus(error.message, "error"));
}

function hideInboundAddTagModal() {
    $("#inboundAddTagModal").hidden = true;
    document.body.classList.remove("modal-open");
    state.mailbox.addTagInboundId = null;
    state.inboundSummary.tagEditInboundId = null;
}

async function populateInboundAddTagQaOptions() {
    if (!state.qaRules || state.qaRules.length === 0) {
        const data = await api("/api/qa/rules");
        state.qaRules = data || [];
    }
    const select = $("#inboundAddTagQaRule");
    const enabledRules = (state.qaRules || []).filter((rule) => rule.enabled !== false);
    select.innerHTML = enabledRules.map((rule) => {
        const label = rule.displayName || rule.keywords?.split(",")[0]?.trim() || `规则#${rule.id}`;
        return `<option value="${escapeHtml(rule.id)}">${escapeHtml(label)}</option>`;
    }).join("") || `<option value="">暂无可用 QA 规则</option>`;
}

async function submitInboundAddTag() {
    const inboundId = state.mailbox.addTagInboundId || state.inboundSummary.tagEditInboundId || state.inboundSummary.selectedId;
    if (!inboundId) return;
    const type = $("#inboundAddTagType").value;
    const operatorName = inboundSummaryOperatorName();
    const payload = operatorName ? { operatorName } : {};
    if (type === "qa") {
        const qaRuleId = Number($("#inboundAddTagQaRule").value);
        if (!qaRuleId) throw new Error("请选择 QA 规则");
        payload.qaRuleId = qaRuleId;
    } else {
        const label = $("#inboundAddTagCustomLabel").value.trim();
        if (!label) throw new Error("请输入自定义标签");
        payload.label = label;
    }
    await api(`/api/inbound-summary/mails/${inboundId}/tags`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
    hideInboundAddTagModal();
    showStatus("标签已添加", "ok");
    state.mailbox.addTagInboundId = null;
    state.inboundSummary.tagEditInboundId = null;
    if (state.mailbox.detailContext?.inboundProcessingId === inboundId) {
        await refreshMailboxInboundTagsAfterChange();
        return;
    }
    await refreshInboundThreadAfterTagChange(inboundId);
}

function bindInboundSummaryEvents() {
    $("#inboundSummaryRefreshBtn")?.addEventListener("click", () => {
        loadInboundSummary().catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundFrom")?.addEventListener("change", () => {
        state.inboundSummary.page = 0;
        loadInboundSummary().catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundTo")?.addEventListener("change", () => {
        state.inboundSummary.page = 0;
        loadInboundSummary().catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundSearch")?.addEventListener("input", (event) => {
        state.inboundSummary.search = event.target.value;
        renderInboundMailList();
    });
    $("#inboundGroupByExpert")?.addEventListener("change", (event) => {
        state.inboundSummary.groupByExpert = event.target.checked;
        renderInboundMailList();
    });
    $("#inboundTagFilters")?.addEventListener("click", (event) => {
        const chip = event.target.closest("[data-tag-key]");
        if (!chip) return;
        const tagKey = chip.dataset.tagKey || "";
        state.inboundSummary.activeTagKey = state.inboundSummary.activeTagKey === tagKey ? "" : tagKey;
        state.inboundSummary.page = 0;
        renderInboundTagFilters(state.inboundSummary.options);
        loadInboundMails().catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundMailList")?.addEventListener("click", (event) => {
        const row = event.target.closest("[data-action='select-inbound-mail']");
        if (!row) return;
        selectInboundMail(Number(row.dataset.id)).catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundPagination")?.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        if (button.dataset.action === "inbound-prev") {
            state.inboundSummary.page = Math.max(0, state.inboundSummary.page - 1);
        }
        if (button.dataset.action === "inbound-next") {
            state.inboundSummary.page += 1;
        }
        loadInboundMails().catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundThread")?.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const inboundId = Number(button.dataset.inboundId);
        if (button.dataset.action === "inbound-auto-tags") {
            inboundAutoApplyTags(inboundId, button).catch((error) => showStatus(error.message, "error"));
            return;
        }
        if (button.dataset.action === "inbound-add-tag-open") {
            if (!inboundId) return;
            state.inboundSummary.tagEditInboundId = inboundId;
            showInboundAddTagModal();
            return;
        }
        if (button.dataset.action === "inbound-remove-tag") {
            const bubble = button.closest(".inbound-thread-bubble");
            const bubbleInboundId = Number(bubble?.querySelector("[data-inbound-id]")?.dataset.inboundId);
            inboundRemoveTag(Number(button.dataset.tagId), bubbleInboundId || null)
                .catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#inboundAddTagType")?.addEventListener("change", (event) => {
        const isQa = event.target.value === "qa";
        $("#inboundAddTagQaGroup").hidden = !isQa;
        $("#inboundAddTagCustomGroup").hidden = isQa;
        if (isQa) {
            populateInboundAddTagQaOptions().catch((error) => showStatus(error.message, "error"));
        }
    });
    $("#inboundAddTagSubmitBtn")?.addEventListener("click", () => {
        submitInboundAddTag().catch((error) => showStatus(error.message, "error"));
    });
    $("#inboundAddTagCloseBtn")?.addEventListener("click", hideInboundAddTagModal);
    $("#inboundAddTagBackdrop")?.addEventListener("click", hideInboundAddTagModal);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#inboundAddTagModal").hidden) {
            hideInboundAddTagModal();
        }
    });
}

function bootstrap() {
    bindEvents();
    initBulkAutoReply();
    initPollLogPanel();
    initLayoutResizer();
    bindAuthEvents();
    checkAuth();
}


// ── Batch Send Task Console ──────────────────────────────────────────────────────────

var batchTaskState = {
    activeTab: "scheduled",
    configs: [],
    query: "",
    editorMode: null,        // "create" | "edit"
    editorId: null,
    editorAutoEnabled: false,
    logConfigId: null,
    logExecutionId: null,
    logRefreshTimer: null,
    manualSource: null,       // config view object or null
    manualDraft: null,        // current draft values
    preloadedTemplates: [],
    preloadedProviders: [],
    preloadedTags: []
};

var batchConfigSearchTimer = null;
var batchManualSourceSearchTimer = null;
var batchManualSourceRequestToken = 0;

function openBatchSendTaskModal() {
    var modal = document.getElementById("batchSendTaskModal");
    if (!modal) return;
    resetBatchTaskState();
    modal.hidden = false;
    document.body.classList.add("modal-open");
    switchBatchSendTab("scheduled");
    loadBatchConfigList();
    preloadBatchSendLookups();
}

function closeBatchSendTaskModal() {
    var modal = document.getElementById("batchSendTaskModal");
    if (!modal) return;
    clearBatchLogRefreshTimer();
    closeBatchLogDrawer();
    closeBatchManualConfirmDialog();
    modal.hidden = true;
    document.body.classList.remove("modal-open");
    resetBatchTaskState();
}

function resetBatchTaskState() {
    batchTaskState = {
        activeTab: "scheduled",
        configs: [],
        query: "",
        editorMode: null,
        editorId: null,
        editorAutoEnabled: false,
        logConfigId: null,
        logExecutionId: null,
        logRefreshTimer: null,
        manualSource: null,
        manualDraft: null,
        preloadedTemplates: batchTaskState.preloadedTemplates,
        preloadedProviders: batchTaskState.preloadedProviders,
        preloadedTags: batchTaskState.preloadedTags
    };
    clearBatchLogRefreshTimer();
    clearTimeout(batchConfigSearchTimer);
    batchConfigSearchTimer = null;
    clearTimeout(batchManualSourceSearchTimer);
    batchManualSourceSearchTimer = null;
    batchManualSourceRequestToken += 1;
}

// ── Preload lookup data (templates, providers) ──────────────────────────────────────

async function preloadBatchSendLookups() {
    try {
        var resp = await api("/api/compose-templates");
        if (Array.isArray(resp)) {
            batchTaskState.preloadedTemplates = resp;
            refreshBatchTemplateSelectors();
        }
    } catch (e) { console.error("Failed to load compose templates", e); }
    await loadBatchTagOptions();
    try {
        var providers = await loadBatchSendTypeProviders("INTRODUCTION");
        if (Array.isArray(providers)) batchTaskState.preloadedProviders = providers;
    } catch (e) { console.error("Failed to load providers", e); }
}

// ── Tab switching ────────────────────────────────────────────────────────────────────

function switchBatchSendTab(tab) {
    batchTaskState.activeTab = tab;
    var tabs = $$(".batch-send-tab");
    tabs.forEach(function(t) {
        t.classList.toggle("is-active", t.dataset.tab === tab);
    });
    document.getElementById("batchScheduledPanel").hidden = (tab !== "scheduled");
    document.getElementById("batchManualPanel").hidden = (tab !== "manual");
    closeBatchLogDrawer();
    clearBatchLogRefreshTimer();
    if (tab === "scheduled") {
        hideBatchConfigEditor();
        loadBatchConfigList();
    } else if (tab === "manual") {
        if (!batchTaskState.manualSource) {
            resetManualExecution({ preserveSource: false });
        }
    }
}

// ── Config List ──────────────────────────────────────────────────────────────────────

async function loadBatchConfigList() {
    var tbody = document.getElementById("batchConfigTableBody");
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="7" class="muted" style="text-align:center;padding:24px;">加载中...</td></tr>';
    try {
        var q = batchTaskState.query || "";
        var params = q ? "?q=" + encodeURIComponent(q) : "";
        var configs = await api("/api/mail/batch-send/configs" + params);
        batchTaskState.configs = Array.isArray(configs) ? configs : [];
        renderBatchConfigTable();
    } catch (e) {
        tbody.innerHTML = '<tr><td colspan="7" class="muted" style="text-align:center;padding:24px;color:#e11d48;">加载失败: ' + escapeHtml(e.message) + '</td></tr>';
    }
}

function handleBatchConfigSearchInput() {
    var input = document.getElementById("batchConfigSearch");
    if (!input) return;
    batchTaskState.query = input.value.trim();
    clearTimeout(batchConfigSearchTimer);
    batchConfigSearchTimer = setTimeout(function() { loadBatchConfigList(); }, 250);
}

function renderBatchConfigTable() {
    var tbody = document.getElementById("batchConfigTableBody");
    if (!tbody) return;
    var configs = batchTaskState.configs;
    var count = document.getElementById("batchConfigCount");
    if (count) count.textContent = "共 " + configs.length + " 个任务";
    if (configs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="muted" style="text-align:center;padding:24px;">暂无定时任务配置</td></tr>';
        return;
    }
    tbody.innerHTML = configs.map(function(c) { return renderBatchConfigRow(c); }).join("");
}

function renderBatchConfigRow(c) {
    var scopeParts = [];
    if (c.funnelLevel) scopeParts.push("漏斗: " + escapeHtml(c.funnelLevel));
    if (Array.isArray(c.tags) && c.tags.length > 0) scopeParts.push("标签: " + escapeHtml(c.tags.join(", ")));
    if (c.emailDomain) scopeParts.push("服务商: " + escapeHtml(c.emailDomain));
    if (c.discipline) scopeParts.push("学科: " + (c.discipline === "STEM" ? "仅理工科" : c.discipline === "HUMANITIES" ? "仅文社科" : escapeHtml(c.discipline)));
    var scopeHtml = scopeParts.length > 0
        ? scopeParts.map(function(s, i) {
            var cls = i === 0 ? "batch-task-scope-line" : "batch-task-scope-line";
            return '<span class="' + cls + '">' + s + '</span>';
        }).join("")
        : '<span class="batch-task-scope-line muted">无限制</span>';

    var planHtml = cronToDisplayText(c.cron);
    var statusHtml = renderBatchConfigStatusToggle(c);

    return '<tr>' +
        '<td><strong>' + escapeHtml(c.configName) + '</strong><br><span class="muted" style="font-size:11px;">' + escapeHtml(c.mailType) + '</span></td>' +
        '<td class="batch-task-scope">' + scopeHtml.substring(0, 300) + '</td>' +
        '<td>' + (c.templateId ? '<span class="badge ok">已指定</span>' : '<span class="badge">默认</span>') + '</td>' +
        '<td>' + escapeHtml(planHtml) + '</td>' +
        '<td><span class="muted" style="font-size:11px;">' + (c.updatedAt ? formatDateTime(c.updatedAt) : "—") + '</span></td>' +
        '<td>' + statusHtml + '</td>' +
        '<td class="batch-task-actions">' +
            '<button class="button small" onclick="openManualTabFromConfig(' + c.id + ')">手动</button>' +
            '<button class="button small" onclick="openBatchConfigEditor(' + c.id + ')">编辑</button>' +
            '<button class="button small" onclick="openBatchConfigLogs(' + c.id + ')">日志</button>' +
            '<button class="button small danger" onclick="deleteBatchConfig(' + c.id + ')">删除</button>' +
        '</td>' +
        '</tr>';
}

function renderBatchConfigStatusToggle(c) {
    var label = c.autoEnabled ? "已启用" : "已停用";
    return '<label class="batch-task-status-toggle">' +
        '<input type="checkbox" aria-label="' + label + '定时任务" ' + (c.autoEnabled ? "checked" : "") + ' onchange="toggleBatchConfigEnabled(' + c.id + ', this.checked)">' +
        '<span class="batch-task-status-switch" aria-hidden="true"></span>' +
        '<span class="batch-task-status-label">' + label + '</span>' +
        '</label>';
}

function cronToDisplayText(cron) {
    if (!cron) return "—";
    var parts = cron.trim().split(/\s+/);
    if (parts.length < 6) return escapeHtml(cron);
    var sec = parts[0], min = parts[1], hour = parts[2], dom = parts[3], mon = parts[4], dow = parts[5];
    if (hour === "*" || hour === "*/1") return "每小时";
    var time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0");
    if (dow && dow !== "?" && dow !== "*") {
        var dowLabel = { "MON": "周一", "TUE": "周二", "WED": "周三", "THU": "周四", "FRI": "周五", "SAT": "周六", "SUN": "周日" }[dow] || dow;
        return dowLabel + " " + time;
    }
    return "每天 " + time;
}

async function toggleBatchConfigEnabled(id, enabled) {
    try {
        var updated = await api("/api/mail/batch-send/configs/" + id + "/enabled", {
            method: "PATCH",
            body: JSON.stringify({ enabled: enabled })
        });
        var idx = batchTaskState.configs.findIndex(function(c) { return c.id === id; });
        if (idx >= 0) batchTaskState.configs[idx] = updated;
        renderBatchConfigTable();
    } catch (e) {
        showStatus("操作失败: " + e.message, "error");
        renderBatchConfigTable();
    }
}

async function deleteBatchConfig(id) {
    if (!confirm("确定删除该定时任务配置？此操作不可撤销。")) return;
    try {
        await api("/api/mail/batch-send/configs/" + id, { method: "DELETE" });
        batchTaskState.configs = batchTaskState.configs.filter(function(c) { return c.id !== id; });
        renderBatchConfigTable();
        showStatus("已删除", "ok");
    } catch (e) {
        showStatus("删除失败: " + e.message, "error");
    }
}

// ── Config Editor (create/edit) ─────────────────────────────────────────────────────

function hideBatchConfigEditor() {
    var editor = document.getElementById("batchConfigEditor");
    if (editor) editor.hidden = true;
    var panel = document.getElementById("batchScheduledPanel");
    if (panel) panel.classList.remove("is-editing");
    var manualTab = document.getElementById("batchManualTab");
    if (manualTab) manualTab.hidden = false;
    batchTaskState.editorMode = null;
    batchTaskState.editorId = null;
    batchTaskState.editorAutoEnabled = false;
}

function openBatchConfigEditor(id) {
    var config = batchTaskState.configs.find(function(c) { return c.id === id; });
    if (!config) return;
    batchTaskState.editorMode = "edit";
    batchTaskState.editorId = id;
    showBatchConfigEditor(config);
}

function showBatchConfigEditorForm() {
    batchTaskState.editorMode = "create";
    batchTaskState.editorId = null;
    showBatchConfigEditor(null);
}

function showBatchConfigEditor(config) {
    var editor = document.getElementById("batchConfigEditor");
    if (!editor) return;
    var panel = document.getElementById("batchScheduledPanel");
    if (panel) {
        panel.classList.add("is-editing");
        panel.scrollTop = 0;
    }
    var manualTab = document.getElementById("batchManualTab");
    if (manualTab) manualTab.hidden = true;
    editor.hidden = false;
    var title = document.getElementById("batchConfigEditorTitle");
    if (title) title.textContent = config ? "编辑定时任务" : "新增定时任务";

    // fill form
    var setVal = function(id, val) { var el = document.getElementById(id); if (el) el.value = val || ""; };
    setVal("batchConfigEditorName", config ? config.configName : "");
    setVal("batchConfigEditorFunnelLevel", config ? (config.funnelLevel || "") : "");
    setBatchTagPickerValue("batchConfigEditorTags", config && Array.isArray(config.tags) ? config.tags : []);
    setVal("batchConfigEditorDiscipline", config ? (config.discipline || "") : "");
    setVal("batchConfigEditorDailyCap", config ? config.dailyCap : "1000");
    setVal("batchConfigEditorRoundSize", config ? config.roundSize : "50");
    setVal("batchConfigEditorPerMailIntervalSec", config ? Math.round((config.perMailIntervalMs || 1000) / 1000) : "1");
    setVal("batchConfigEditorPerRoundIntervalSec", config ? Math.round((config.perRoundIntervalMs || 60000) / 1000) : "60");
    setVal("batchConfigEditorSelfCheckTtlMin", config ? config.selfCheckTtlMinutes : "30");
    batchTaskState.editorAutoEnabled = config ? Boolean(config.autoEnabled) : false;

    // Parse cron to frequency + time
    var freq = "daily", time = "09:00";
    if (config && config.cron) {
        var cronParts = config.cron.trim().split(/\s+/);
        if (cronParts.length >= 5) {
            var hour = cronParts[2], min = cronParts[1], dow = cronParts[5];
            if (hour === "*" || hour === "*/1") { freq = "hourly"; time = ""; }
            else if (dow && dow !== "?" && dow !== "*") { freq = "weekly"; time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0"); }
            else { freq = "daily"; time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0"); }
        }
    }
    setVal("batchConfigEditorFrequency", freq);
    setVal("batchConfigEditorTime", time);
    var timeField = document.getElementById("batchConfigEditorTimeField");
    if (timeField) timeField.style.display = freq === "hourly" ? "none" : "";

    // Fill template selector and provider dropdown
    fillBatchConfigEditorTemplateSelector(config ? config.templateId : null);
    fillBatchConfigEditorProviderSelect(config ? config.emailDomain : "");
}

function fillBatchConfigEditorTemplateSelector(selectedId) {
    var select = document.getElementById("batchConfigEditorTemplateId");
    if (!select) return;
    var html = '<option value="">系统默认介绍邮件模板</option>';
    html += supportedBatchComposeTemplates().map(function(t) { return '<option value="' + t.id + '">' + escapeHtml(t.templateName) + '</option>'; }).join("");
    select.innerHTML = html;
    select.value = selectedId ? String(selectedId) : "";
}

function supportedBatchComposeTemplates() {
    return (batchTaskState.preloadedTemplates || []).filter(function(t) {
        return t.enabled && (t.mailType === "INTRODUCTION" || t.mailType === "MATERIAL_REMINDER");
    });
}

function resolveBatchTemplateMailType(templateId) {
    if (!templateId) return "INTRODUCTION";
    var template = supportedBatchComposeTemplates().find(function(t) {
        return Number(t.id) === Number(templateId);
    });
    return template ? template.mailType : "INTRODUCTION";
}

function refreshBatchTemplateSelectors() {
    var editorSelect = document.getElementById("batchConfigEditorTemplateId");
    if (editorSelect) {
        var editorConfig = batchTaskState.configs.find(function(c) {
            return Number(c.id) === Number(batchTaskState.editorId);
        });
        var editorTemplateId = editorConfig ? editorConfig.templateId : (editorSelect.value || null);
        fillBatchConfigEditorTemplateSelector(editorTemplateId);
    }
    var manualSelect = document.getElementById("batchManualTemplateId");
    if (manualSelect) {
        var manualTemplateId = batchTaskState.manualDraft && batchTaskState.manualDraft.templateId != null
            ? batchTaskState.manualDraft.templateId
            : (manualSelect.value || null);
        fillBatchManualTemplateSelector(manualTemplateId);
    }
}

function normalizeBatchTags(value) {
    var source = Array.isArray(value) ? value : String(value || "").split(",");
    var seen = {};
    return source.map(function(tag) { return String(tag || "").trim(); }).filter(function(tag) {
        if (!tag || seen[tag]) return false;
        seen[tag] = true;
        return true;
    });
}

function mergeBatchTagOptions(optionLists, extraTags) {
    var merged = [];
    (optionLists || []).forEach(function(list) {
        (Array.isArray(list) ? list : []).forEach(function(item) {
            var tag = typeof item === "string" ? item : item && item.tag;
            merged.push(tag);
        });
    });
    merged = merged.concat(normalizeBatchTags(extraTags));
    return normalizeBatchTags(merged);
}

async function loadBatchTagOptions() {
    var levels = ["CANDIDATE", "APPLICATION"];
    var results = await Promise.all(levels.map(function(level) {
        return api("/api/experts/tags/aggregation?level=" + encodeURIComponent(level)).catch(function(e) {
            console.error("Failed to load batch tags for " + level, e);
            return [];
        });
    }));
    var currentTags = readBatchTagPickerValue("batchConfigEditorTags")
        .concat(readBatchTagPickerValue("batchManualTags"));
    batchTaskState.preloadedTags = mergeBatchTagOptions(results, currentTags);
    renderBatchTagPicker("batchConfigEditorTags");
    renderBatchTagPicker("batchManualTags");
}

function readBatchTagPickerValue(valueId) {
    var input = document.getElementById(valueId);
    return normalizeBatchTags(input ? input.value : "");
}

function setBatchTagPickerValue(valueId, tags) {
    var input = document.getElementById(valueId);
    if (!input) return;
    input.value = normalizeBatchTags(tags).join(",");
    renderBatchTagPicker(valueId);
}

function batchTagDisplayName(tag) {
    return typeof expertTagLabels !== "undefined" && expertTagLabels[tag] ? expertTagLabels[tag] : tag;
}

function renderBatchTagPicker(valueId) {
    var search = document.getElementById(valueId + "Search");
    var chips = document.getElementById(valueId + "Chips");
    var dropdown = document.getElementById(valueId + "Dropdown");
    if (!search || !chips || !dropdown) return;
    var selected = readBatchTagPickerValue(valueId);
    var options = mergeBatchTagOptions([batchTaskState.preloadedTags || []], selected);
    var query = search.value.trim().toLowerCase();
    var filtered = options.filter(function(tag) {
        return !query || tag.toLowerCase().includes(query) || batchTagDisplayName(tag).toLowerCase().includes(query);
    });

    chips.innerHTML = selected.map(function(tag) {
        return '<span class="batch-tag-picker-chip">' + escapeHtml(batchTagDisplayName(tag)) +
            '<button type="button" data-remove-tag="' + escapeHtml(tag) + '" aria-label="移除标签 ' + escapeHtml(batchTagDisplayName(tag)) + '">×</button></span>';
    }).join("");
    dropdown.innerHTML = filtered.length > 0 ? filtered.map(function(tag) {
        var checked = selected.includes(tag);
        return '<button type="button" class="batch-tag-picker-option' + (checked ? ' is-selected' : '') +
            '" role="option" aria-selected="' + checked + '" data-tag="' + escapeHtml(tag) + '">' +
            '<span class="batch-tag-picker-check" aria-hidden="true">' + (checked ? '✓' : '') + '</span>' +
            '<span>' + escapeHtml(batchTagDisplayName(tag)) + '</span></button>';
    }).join("") : '<div class="batch-tag-picker-empty">没有匹配标签</div>';
}

function notifyBatchTagPickerChanged(valueId) {
    if (valueId !== "batchManualTags") return;
    if (!batchTaskState.manualDraft) batchTaskState.manualDraft = {};
    batchTaskState.manualDraft.tags = readBatchTagPickerValue(valueId);
    computeAndRenderDiffs();
}

function toggleBatchTagPickerValue(valueId, tag) {
    var selected = readBatchTagPickerValue(valueId);
    var normalizedTag = String(tag || "").trim();
    if (!normalizedTag) return;
    var index = selected.indexOf(normalizedTag);
    if (index >= 0) selected.splice(index, 1);
    else selected.push(normalizedTag);
    setBatchTagPickerValue(valueId, selected);
    notifyBatchTagPickerChanged(valueId);
}

function openBatchTagPicker(valueId) {
    var dropdown = document.getElementById(valueId + "Dropdown");
    var search = document.getElementById(valueId + "Search");
    renderBatchTagPicker(valueId);
    if (dropdown) dropdown.hidden = false;
    if (search) search.setAttribute("aria-expanded", "true");
}

function closeBatchTagPicker(valueId) {
    var dropdown = document.getElementById(valueId + "Dropdown");
    var search = document.getElementById(valueId + "Search");
    if (dropdown) dropdown.hidden = true;
    if (search) search.setAttribute("aria-expanded", "false");
}

function bindBatchTagPicker(valueId) {
    var picker = document.querySelector('[data-tag-picker="' + valueId + '"]');
    var search = document.getElementById(valueId + "Search");
    var chips = document.getElementById(valueId + "Chips");
    var dropdown = document.getElementById(valueId + "Dropdown");
    if (!picker || !search || !chips || !dropdown) return;
    search.addEventListener("focus", function() { openBatchTagPicker(valueId); });
    search.addEventListener("input", function() { openBatchTagPicker(valueId); });
    search.addEventListener("keydown", function(event) {
        if (event.key === "Escape") closeBatchTagPicker(valueId);
    });
    chips.addEventListener("click", function(event) {
        var button = event.target.closest("[data-remove-tag]");
        if (button) toggleBatchTagPickerValue(valueId, button.dataset.removeTag);
    });
    dropdown.addEventListener("click", function(event) {
        var option = event.target.closest("[data-tag]");
        if (option) toggleBatchTagPickerValue(valueId, option.dataset.tag);
    });
    document.addEventListener("click", function(event) {
        if (!picker.contains(event.target)) closeBatchTagPicker(valueId);
    });
    renderBatchTagPicker(valueId);
}

function fillBatchConfigEditorProviderSelect(selected) {
    var select = document.getElementById("batchConfigEditorEmailDomain");
    if (!select) return;
    var providers = batchTaskState.preloadedProviders || [];
    var html = '<option value="">全部</option>';
    if (typeof providers[0] === "string") {
        html += providers.map(function(p) { return '<option value="' + escapeHtml(p) + '">' + escapeHtml(p) + '</option>'; }).join("");
    } else {
        html += providers.map(function(p) { return '<option value="' + escapeHtml(p.domain || p) + '">' + escapeHtml(p.domain || p) + '</option>'; }).join("");
    }
    select.innerHTML = html;
    select.value = selected || "";
}

async function saveBatchConfigEditor() {
    var val = function(id) { var el = document.getElementById(id); return el ? el.value : ""; };
    var name = val("batchConfigEditorName").trim();
    if (!name) { showStatus("请输入任务名称", "error"); return; }

    var freq = val("batchConfigEditorFrequency") || "daily";
    var timeParts = (val("batchConfigEditorTime") || "09:00").split(":");
    var hour = parseInt(timeParts[0] || "0", 10);
    var min = parseInt(timeParts[1] || "0", 10);
    var cron;
    if (freq === "hourly") cron = "0 0 * * * ?";
    else if (freq === "weekly") cron = "0 " + min + " " + hour + " ? * MON";
    else cron = "0 " + min + " " + hour + " * * ?";

    var tags = readBatchTagPickerValue("batchConfigEditorTags");

    var templateId = null;
    var rawTemplate = val("batchConfigEditorTemplateId");
    if (rawTemplate) {
        var n = Number(rawTemplate);
        if (Number.isFinite(n) && n > 0) templateId = n;
    }

    var payload = {
        configName: name,
        autoEnabled: Boolean(batchTaskState.editorAutoEnabled),
        cron: cron,
        dailyCap: Number(val("batchConfigEditorDailyCap")) || 1000,
        roundSize: Number(val("batchConfigEditorRoundSize")) || 50,
        perMailIntervalMs: (Number(val("batchConfigEditorPerMailIntervalSec")) || 0) * 1000,
        perRoundIntervalMs: (Number(val("batchConfigEditorPerRoundIntervalSec")) || 0) * 1000,
        selfCheckTtlMinutes: Number(val("batchConfigEditorSelfCheckTtlMin")) || 30,
        funnelLevel: val("batchConfigEditorFunnelLevel") || null,
        tags: tags,
        emailDomain: val("batchConfigEditorEmailDomain") || null,
        discipline: val("batchConfigEditorDiscipline") || null,
        templateId: templateId
    };

    if (payload.roundSize < 1) { showStatus("每轮数量需 ≥ 1", "error"); return; }
    if (payload.dailyCap < payload.roundSize) { showStatus("每批上限需 ≥ 每轮数量", "error"); return; }
    if (payload.perMailIntervalMs < 0) { showStatus("每封间隔需 ≥ 0", "error"); return; }
    if (payload.perRoundIntervalMs < 0) { showStatus("每轮间隔需 ≥ 0", "error"); return; }
    if (payload.selfCheckTtlMinutes < 1) { showStatus("自检 TTL 需 ≥ 1", "error"); return; }

    var btn = document.getElementById("batchConfigEditorSaveBtn");
    if (btn) btn.disabled = true;
    try {
        if (batchTaskState.editorMode === "edit" && batchTaskState.editorId) {
            await api("/api/mail/batch-send/configs/" + batchTaskState.editorId, {
                method: "PUT", body: JSON.stringify(payload)
            });
        } else {
            await api("/api/mail/batch-send/configs", {
                method: "POST", body: JSON.stringify(payload)
            });
        }
        hideBatchConfigEditor();
        loadBatchConfigList();
        showStatus("保存成功", "ok");
    } catch (e) {
        showStatus("保存失败: " + e.message, "error");
    } finally {
        if (btn) btn.disabled = false;
    }
}

// ── Manual Execution Tab ────────────────────────────────────────────────────────────

function openManualTabFromConfig(id) {
    var config = batchTaskState.configs.find(function(c) { return Number(c.id) === Number(id); });
    if (!config) return;
    applyBatchManualSource(config);
    switchBatchSendTab("manual");
}

function applyBatchManualSource(config) {
    if (!config) return;
    batchTaskState.manualSource = deepCloneConfig(config);
    batchTaskState.manualDraft = deepCloneConfig(config);

    var input = document.getElementById("batchManualSourceQuery");
    if (input) input.value = config.configName || "";
    var sourceId = document.getElementById("batchManualSourceId");
    if (sourceId) sourceId.value = config.id == null ? "" : String(config.id);
    var sourceUpdatedAt = document.getElementById("batchManualSourceUpdatedAt");
    if (sourceUpdatedAt) sourceUpdatedAt.value = config.updatedAt || "";

    updateManualSourceInfo();
    fillManualFormFromDraft();
}

function resetManualExecution(opts) {
    if (opts && opts.preserveSource === false) {
        batchTaskState.manualSource = null;
        batchTaskState.manualDraft = null;
    }
    var sourceQuery = document.getElementById("batchManualSourceQuery");
    if (sourceQuery) sourceQuery.value = "";
    var sourceId = document.getElementById("batchManualSourceId");
    if (sourceId) sourceId.value = "";
    var sourceUpdatedAt = document.getElementById("batchManualSourceUpdatedAt");
    if (sourceUpdatedAt) sourceUpdatedAt.value = "";

    updateManualSourceInfo();
    fillManualFormDefaults();
}

function deepCloneConfig(c) {
    return {
        id: c.id || null,
        templateId: c.templateId || null,
        mailType: c.mailType || "INTRODUCTION",
        funnelLevel: c.funnelLevel || "",
        tags: Array.isArray(c.tags) ? c.tags.slice() : [],
        emailDomain: c.emailDomain || "",
        discipline: c.discipline || "",
        dailyCap: c.dailyCap || 1000,
        roundSize: c.roundSize || 50,
        perMailIntervalMs: c.perMailIntervalMs || 1000,
        perRoundIntervalMs: c.perRoundIntervalMs || 60000,
        selfCheckTtlMinutes: c.selfCheckTtlMinutes || 30,
        configName: c.configName || "",
        updatedAt: c.updatedAt || null
    };
}

function fillManualFormDefaults() {
    batchTaskState.manualDraft = {
        templateId: null,
        mailType: "INTRODUCTION",
        funnelLevel: "",
        tags: [],
        emailDomain: "",
        discipline: "",
        dailyCap: 1000,
        roundSize: 50,
        perMailIntervalMs: 1000,
        perRoundIntervalMs: 60000,
        selfCheckTtlMinutes: 30,
        configName: "",
        updatedAt: null
    };
    fillManualFormFromDraft();
}

function fillManualFormFromDraft() {
    var d = batchTaskState.manualDraft;
    if (!d) { fillManualFormDefaults(); return; }

    var setVal = function(id, v) { var el = document.getElementById(id); if (el) el.value = v || ""; };
    setVal("batchManualTemplateId", d.templateId ? String(d.templateId) : "");
    setVal("batchManualFunnelLevel", d.funnelLevel || "");
    setBatchTagPickerValue("batchManualTags", Array.isArray(d.tags) ? d.tags : []);
    setVal("batchManualEmailDomain", d.emailDomain || "");
    setVal("batchManualDiscipline", d.discipline || "");
    setVal("batchManualDailyCap", d.dailyCap);
    setVal("batchManualRoundSize", d.roundSize);
    setVal("batchManualPerMailIntervalSec", Math.round((d.perMailIntervalMs || 1000) / 1000));
    setVal("batchManualPerRoundIntervalSec", Math.round((d.perRoundIntervalMs || 60000) / 1000));
    setVal("batchManualSelfCheckTtlMin", d.selfCheckTtlMinutes);

    if (batchTaskState.manualSource) {
        fillBatchManualTemplateSelector(d.templateId);
        fillBatchManualProviderSelect(d.emailDomain);
    } else {
        fillBatchManualTemplateSelector(d.templateId);
        fillBatchManualProviderSelect("");
    }

    computeAndRenderDiffs();
}

function fillBatchManualTemplateSelector(selectedId) {
    var select = document.getElementById("batchManualTemplateId");
    if (!select) return;
    var html = '<option value="">系统默认介绍邮件模板</option>';
    html += supportedBatchComposeTemplates().map(function(t) { return '<option value="' + t.id + '">' + escapeHtml(t.templateName) + '</option>'; }).join("");
    select.innerHTML = html;
    select.value = selectedId ? String(selectedId) : "";
}

function fillBatchManualProviderSelect(selected) {
    var select = document.getElementById("batchManualEmailDomain");
    if (!select) return;
    var providers = batchTaskState.preloadedProviders || [];
    var html = '<option value="">全部</option>';
    if (typeof providers[0] === "string") {
        html += providers.map(function(p) { return '<option value="' + escapeHtml(p) + '">' + escapeHtml(p) + '</option>'; }).join("");
    } else {
        html += providers.map(function(p) { return '<option value="' + escapeHtml(p.domain || p) + '">' + escapeHtml(p.domain || p) + '</option>'; }).join("");
    }
    select.innerHTML = html;
    select.value = selected || "";
}

function updateManualSourceInfo() {
    var info = document.getElementById("batchManualSourceInfo");
    var clearBtn = document.getElementById("batchManualClearSourceBtn");
    var source = batchTaskState.manualSource;

    if (!info) return;
    if (source) {
        info.textContent = "来源：" + source.configName + " | 更新于 " + (source.updatedAt ? formatDateTime(source.updatedAt) : "—");
        if (clearBtn) clearBtn.hidden = false;
    } else {
        info.textContent = "当前：独立手动执行（未关联定时配置）";
        if (clearBtn) clearBtn.hidden = true;
    }
}

function clearManualSource() {
    resetManualExecution({ preserveSource: false });
    updateManualSourceInfo();
}

function detachBatchManualSourcePreservingDraft() {
    if (!batchTaskState.manualSource) return;
    batchTaskState.manualDraft = Object.assign({}, batchTaskState.manualDraft || {}, readManualFormValues());
    batchTaskState.manualSource = null;
    var sourceId = document.getElementById("batchManualSourceId");
    if (sourceId) sourceId.value = "";
    var sourceUpdatedAt = document.getElementById("batchManualSourceUpdatedAt");
    if (sourceUpdatedAt) sourceUpdatedAt.value = "";
    updateManualSourceInfo();
    clearAllDiffMarkers();
}

// ── Diff Detection ──────────────────────────────────────────────────────────────────

function readManualFormValues() {
    var val = function(id) { var el = document.getElementById(id); return el ? el.value : ""; };
    var parseNum = function(id) {
        var raw = val(id);
        if (raw === "" || raw == null) return NaN;
        var n = Number(raw);
        return Number.isFinite(n) ? n : NaN;
    };
    var parseNumSec = function(id) {
        var n = parseNum(id);
        return Number.isFinite(n) ? n * 1000 : NaN;
    };
    var rawTemplateId = val("batchManualTemplateId");
    var templateId = rawTemplateId ? Number(rawTemplateId) : null;
    return {
        templateId: templateId,
        mailType: resolveBatchTemplateMailType(templateId),
        funnelLevel: val("batchManualFunnelLevel") || null,
        tags: readBatchTagPickerValue("batchManualTags"),
        emailDomain: val("batchManualEmailDomain") || null,
        discipline: val("batchManualDiscipline") || null,
        dailyCap: parseNum("batchManualDailyCap"),
        roundSize: parseNum("batchManualRoundSize"),
        perMailIntervalMs: parseNumSec("batchManualPerMailIntervalSec"),
        perRoundIntervalMs: parseNumSec("batchManualPerRoundIntervalSec"),
        selfCheckTtlMinutes: parseNum("batchManualSelfCheckTtlMin")
    };
}

function normalizeManualSnapshot(v) {
    return {
        templateId: v.templateId || null,
        funnelLevel: (v.funnelLevel || "").trim() || null,
        tags: (Array.isArray(v.tags) ? v.tags.slice() : []).map(function(t) { return t.trim(); }).filter(function(t) { return t.length > 0; }).sort().filter(function(t, i, arr) { return arr.indexOf(t) === i; }),
        emailDomain: (v.emailDomain || "").trim() || null,
        discipline: (v.discipline || "").trim() || null,
        dailyCap: Number.isFinite(v.dailyCap) ? v.dailyCap : null,
        roundSize: Number.isFinite(v.roundSize) ? v.roundSize : null,
        perMailIntervalMs: Number.isFinite(v.perMailIntervalMs) ? v.perMailIntervalMs : null,
        perRoundIntervalMs: Number.isFinite(v.perRoundIntervalMs) ? v.perRoundIntervalMs : null,
        selfCheckTtlMinutes: Number.isFinite(v.selfCheckTtlMinutes) ? v.selfCheckTtlMinutes : null
    };
}

function formatManualDiffValue(key, value) {
    if (key === "templateId") {
        if (!value) return "系统默认介绍邮件模板";
        var template = supportedBatchComposeTemplates().find(function(item) {
            return Number(item.id) === Number(value);
        });
        return template ? template.templateName : "模板 #" + value;
    }
    if (key === "funnelLevel") return value || "全部层级";
    if (key === "emailDomain") return value || "全部服务商";
    if (key === "discipline") {
        if (!value) return "全部学科";
        if (value === "STEM") return "仅理工科";
        if (value === "HUMANITIES") return "仅文社科";
        return String(value);
    }
    if (key === "tags") {
        var tags = Array.isArray(value) ? value : [];
        return tags.length > 0 ? tags.join(", ") : "(无)";
    }
    return value == null || value === "" ? "未设置" : String(value);
}

function computeManualDiffs() {
    if (!batchTaskState.manualSource) return [];
    var base = normalizeManualSnapshot(batchTaskState.manualSource);
    var draft = readManualFormValues();
    var dn = normalizeManualSnapshot(draft);

    var fieldDefs = [
        { key: "templateId", label: "模板" },
        { key: "funnelLevel", label: "漏斗层级" },
        { key: "tags", label: "标签" },
        { key: "emailDomain", label: "邮箱服务商" },
        { key: "discipline", label: "学科" },
        { key: "dailyCap", label: "日限额" },
        { key: "roundSize", label: "每轮数量" },
        { key: "perMailIntervalMs", label: "每封间隔" },
        { key: "perRoundIntervalMs", label: "每轮间隔" },
        { key: "selfCheckTtlMinutes", label: "自检 TTL" }
    ];

    var diffs = [];
    fieldDefs.forEach(function(fd) {
        var oldVal = base[fd.key];
        var newVal = dn[fd.key];
        if (fd.key === "tags") {
            var oldArr = (oldVal || []).join(", ");
            var newArr = (newVal || []).join(", ");
            if (oldArr !== newArr) {
                diffs.push({ key: fd.key, label: fd.label, oldDisplay: formatManualDiffValue(fd.key, oldVal), newDisplay: formatManualDiffValue(fd.key, newVal) });
            }
        } else if (fd.key === "templateId") {
            if (String(oldVal || "") !== String(newVal || "")) {
                diffs.push({ key: fd.key, label: fd.label, oldDisplay: formatManualDiffValue(fd.key, oldVal), newDisplay: formatManualDiffValue(fd.key, newVal) });
            }
        } else {
            if (String(oldVal || "") !== String(newVal || "")) {
                diffs.push({ key: fd.key, label: fd.label, oldDisplay: formatManualDiffValue(fd.key, oldVal), newDisplay: formatManualDiffValue(fd.key, newVal) });
            }
        }
    });
    return diffs;
}

function computeAndRenderDiffs() {
    if (!batchTaskState.manualSource) {
        clearAllDiffMarkers();
        return;
    }
    var diffs = computeManualDiffs();
    var diffKeys = {};
    diffs.forEach(function(d) { diffKeys[d.key] = d; });
    var fieldMap = {
        templateId: "manualFieldTemplate",
        funnelLevel: "manualFieldFunnelLevel",
        tags: "manualFieldTags",
        emailDomain: "manualFieldEmailDomain",
        discipline: "manualFieldDiscipline",
        dailyCap: "manualFieldDailyCap",
        roundSize: "manualFieldRoundSize",
        perMailIntervalMs: "manualFieldPerMailIntervalSec",
        perRoundIntervalMs: "manualFieldPerRoundIntervalSec",
        selfCheckTtlMinutes: "manualFieldSelfCheckTtlMin"
    };

    Object.keys(fieldMap).forEach(function(key) {
        var el = document.getElementById(fieldMap[key]);
        if (!el) return;
        var badge = el.querySelector(".batch-config-diff-badge");
        var original = el.querySelector(".batch-config-diff-original");
        if (diffKeys[key]) {
            el.classList.add("is-config-diff");
            if (badge) badge.hidden = false;
            if (original) {
                original.hidden = false;
                original.textContent = "原：" + diffKeys[key].oldDisplay;
            }
        } else {
            el.classList.remove("is-config-diff");
            if (badge) badge.hidden = true;
            if (original) original.hidden = true;
        }
    });
}

function clearAllDiffMarkers() {
    var fields = ["manualFieldTemplate", "manualFieldFunnelLevel", "manualFieldTags", "manualFieldEmailDomain",
        "manualFieldDiscipline", "manualFieldDailyCap", "manualFieldRoundSize",
        "manualFieldPerMailIntervalSec", "manualFieldPerRoundIntervalSec", "manualFieldSelfCheckTtlMin"];
    fields.forEach(function(id) {
        var el = document.getElementById(id);
        if (!el) return;
        el.classList.remove("is-config-diff");
        var badge = el.querySelector(".batch-config-diff-badge");
        var original = el.querySelector(".batch-config-diff-original");
        if (badge) badge.hidden = true;
        if (original) original.hidden = true;
    });
}

// ── Confirm Dialog ──────────────────────────────────────────────────────────────────

function showBatchManualConfirm() {
    var source = batchTaskState.manualSource;
    var diffs = source ? computeManualDiffs() : [];
    var title = document.getElementById("batchManualConfirmTitle");
    var body = document.getElementById("batchManualConfirmBody");
    var dialog = document.getElementById("batchManualConfirmDialog");
    if (!title || !body || !dialog) return;

    if (source && diffs.length > 0) {
        title.textContent = "确认按修改后的配置执行？";
        var tableRows = diffs.map(function(d) {
            return '<tr><td>' + escapeHtml(d.label) + '</td>' +
                '<td class="batch-manual-confirm-old">' + escapeHtml(d.oldDisplay) + '</td>' +
                '<td class="batch-manual-confirm-new">' + escapeHtml(d.newDisplay) + '</td></tr>';
        }).join("");
        body.innerHTML =
            '<p class="batch-manual-confirm-warning">以下参数与定时配置存在差异，执行不影响定时配置。</p>' +
            '<table class="batch-manual-confirm-table">' +
            '<thead><tr><th>字段</th><th>原值</th><th>新值</th></tr></thead>' +
            '<tbody>' + tableRows + '</tbody></table>';
    } else if (source) {
        title.textContent = "确认执行该配置？";
        body.innerHTML =
            '<div class="batch-manual-confirm-summary">' +
            '<strong>' + escapeHtml(source.configName) + '</strong><br>' +
            '日限额: ' + source.dailyCap + ' 封 · 每轮: ' + source.roundSize + ' 封<br>' +
            '来源配置: ' + escapeHtml(source.configName) +
            '</div>';
    } else {
        title.textContent = "确认独立手动执行？";
        body.innerHTML =
            '<div class="batch-manual-confirm-summary">' +
            '未关联定时配置，本次参数不会保存。<br>' +
            '日限额: ' + escapeHtml(String(document.getElementById("batchManualDailyCap")?.value || "1000")) +
            ' 封 · 每轮: ' + escapeHtml(String(document.getElementById("batchManualRoundSize")?.value || "50")) + ' 封' +
            '</div>' +
            '<p class="batch-manual-confirm-warning">此为独立执行，不关联任何定时配置。</p>';
    }
    dialog.hidden = false;
}

function closeBatchManualConfirmDialog() {
    var dialog = document.getElementById("batchManualConfirmDialog");
    if (dialog) dialog.hidden = true;
    document.getElementById("batchManualConfirmOkBtn").disabled = false;
}

async function confirmManualExecution() {
    var okBtn = document.getElementById("batchManualConfirmOkBtn");
    if (okBtn) okBtn.disabled = true;

    var source = batchTaskState.manualSource;
    var values = readManualFormValues();
    var snapshot = {
        mailType: values.mailType,
        dailyCap: Number.isFinite(values.dailyCap) ? values.dailyCap : 1000,
        roundSize: Number.isFinite(values.roundSize) ? values.roundSize : 50,
        perMailIntervalMs: Number.isFinite(values.perMailIntervalMs) ? values.perMailIntervalMs : 1000,
        perRoundIntervalMs: Number.isFinite(values.perRoundIntervalMs) ? values.perRoundIntervalMs : 60000,
        selfCheckTtlMinutes: Number.isFinite(values.selfCheckTtlMinutes) ? values.selfCheckTtlMinutes : 30,
        funnelLevel: values.funnelLevel,
        tags: values.tags,
        emailDomain: values.emailDomain,
        discipline: values.discipline,
        templateId: values.templateId
    };

    try {
        var response = await api("/api/mail/batch-send/manual-executions", {
            method: "POST",
            body: JSON.stringify({
                sourceConfigId: source ? source.id : null,
                sourceUpdatedAt: source ? source.updatedAt : null,
                snapshot: snapshot
            })
        });
        closeBatchManualConfirmDialog();
        showStatus("执行已启动 executionId: " + (response.executionId || "—"), "ok");
        if (source) {
            openBatchConfigLogs(source.id, response.executionId);
        }
    } catch (e) {
        showStatus("执行失败: " + e.message, "error");
        if (okBtn) okBtn.disabled = false;
    }
}

function handleManualExecute() {
    var raw = readManualFormValues();
    if (!Number.isFinite(raw.dailyCap) || raw.dailyCap < 1) {
        showStatus("日限额须为 ≥ 1 的有效数字", "error"); return;
    }
    if (!Number.isFinite(raw.roundSize) || raw.roundSize < 1) {
        showStatus("每轮数量须为 ≥ 1 的有效数字", "error"); return;
    }
    if (!Number.isFinite(raw.selfCheckTtlMinutes) || raw.selfCheckTtlMinutes < 1) {
        showStatus("自检 TTL 须为 ≥ 1 的有效数字", "error"); return;
    }
    if (!Number.isFinite(raw.perMailIntervalMs) || raw.perMailIntervalMs < 0) {
        showStatus("每封间隔须为 ≥ 0 的有效数字（秒）", "error"); return;
    }
    if (!Number.isFinite(raw.perRoundIntervalMs) || raw.perRoundIntervalMs < 0) {
        showStatus("每轮间隔须为 ≥ 0 的有效数字（秒）", "error"); return;
    }
    if (raw.dailyCap < raw.roundSize) {
        showStatus("日限额需 ≥ 每轮数量", "error"); return;
    }
    showBatchManualConfirm();
}

// ── Manual Source Search ────────────────────────────────────────────────────────────

var batchManualSourceSearchResults = [];

function handleManualSourceSearch() {
    var input = document.getElementById("batchManualSourceQuery");
    if (!input) return;
    var source = batchTaskState.manualSource;
    if (source && input.value.trim() !== String(source.configName || "").trim()) {
        detachBatchManualSourcePreservingDraft();
    }
    clearTimeout(batchManualSourceSearchTimer);
    batchManualSourceSearchTimer = setTimeout(function() {
        loadBatchManualSourceOptions(input.value);
    }, 160);
}

async function loadBatchManualSourceOptions(query) {
    var token = ++batchManualSourceRequestToken;
    var q = (query || "").trim();
    var url = "/api/mail/batch-send/configs" + (q ? "?q=" + encodeURIComponent(q) : "");
    try {
        var configs = await api(url);
        if (token !== batchManualSourceRequestToken) return;
        batchManualSourceSearchResults = Array.isArray(configs) ? configs : [];
        if (batchManualSourceSearchResults.length === 0) {
            renderBatchManualSourceEmpty();
        } else {
            renderBatchManualSourceDropdown(batchManualSourceSearchResults);
        }
    } catch (e) {
        if (token !== batchManualSourceRequestToken) return;
        console.error("Source search failed", e);
        renderBatchManualSourceEmpty("任务加载失败");
    }
}

function renderBatchManualSourceDropdown(configs) {
    var dropdown = document.getElementById("batchManualSourceDropdown");
    if (!dropdown) return;
    dropdown.innerHTML = configs.map(function(c) {
        return '<button type="button" class="batch-manual-source-dropdown-item" role="option" data-id="' + c.id + '">' +
            '<strong>' + escapeHtml(c.configName) + '</strong><br>' +
            '<span style="font-size:11px;color:#94a3b8;">' + escapeHtml(c.mailType) + ' | 更新于 ' + (c.updatedAt ? formatDateTime(c.updatedAt) : "—") + '</span>' +
            '</button>';
    }).join("");
    dropdown.hidden = false;
    var input = document.getElementById("batchManualSourceQuery");
    if (input) input.setAttribute("aria-expanded", "true");
    dropdown.querySelectorAll(".batch-manual-source-dropdown-item").forEach(function(item) {
        item.addEventListener("click", function() {
            var id = Number(item.dataset.id);
            selectBatchManualSource(id);
        });
    });
}

function renderBatchManualSourceEmpty(message) {
    var dropdown = document.getElementById("batchManualSourceDropdown");
    if (!dropdown) return;
    dropdown.innerHTML = '<div class="batch-manual-source-dropdown-empty">' + escapeHtml(message || "没有匹配的定时任务") + '</div>';
    dropdown.hidden = false;
    var input = document.getElementById("batchManualSourceQuery");
    if (input) input.setAttribute("aria-expanded", "true");
}

function closeBatchManualSourceDropdown() {
    var dropdown = document.getElementById("batchManualSourceDropdown");
    if (dropdown) dropdown.hidden = true;
    var input = document.getElementById("batchManualSourceQuery");
    if (input) input.setAttribute("aria-expanded", "false");
    batchManualSourceRequestToken += 1;
    batchManualSourceSearchResults = [];
}

function selectBatchManualSource(id) {
    var config = batchManualSourceSearchResults.find(function(c) { return Number(c.id) === Number(id); });
    if (!config) return;
    closeBatchManualSourceDropdown();

    // If current draft has diffs from baseline, confirm before overwriting
    if (batchTaskState.manualSource && batchTaskState.manualDraft) {
        var diffs = computeManualDiffs();
        if (diffs.length > 0) {
            if (!confirm("当前已修改配置，切换来源将放弃修改。确定继续？")) {
                return;
            }
        }
    }

    applyBatchManualSource(config);
}

// ── Config Logs ────────────────────────────────────────────────────────────────────

function openBatchConfigLogs(configId, executionId) {
    batchTaskState.logConfigId = configId;
    batchTaskState.logExecutionId = executionId || null;
    var drawer = document.getElementById("batchExecutionLogDrawer");
    if (drawer) drawer.hidden = false;
    clearBatchLogRefreshTimer();
    loadBatchLogExecutions(configId, executionId);
}

function closeBatchLogDrawer() {
    var drawer = document.getElementById("batchExecutionLogDrawer");
    if (drawer) drawer.hidden = true;
    clearBatchLogRefreshTimer();
    batchTaskState.logConfigId = null;
    batchTaskState.logExecutionId = null;
}

function clearBatchLogRefreshTimer() {
    if (batchTaskState.logRefreshTimer) {
        clearInterval(batchTaskState.logRefreshTimer);
        batchTaskState.logRefreshTimer = null;
    }
}

async function loadBatchLogExecutions(configId, executionId) {
    var select = document.getElementById("batchLogExecutionSelect");
    if (select) {
        select.innerHTML = '<option value="">加载中...</option>';
    }
    try {
        var executions = await api("/api/mail/batch-send/configs/" + configId + "/executions?limit=50");
        if (batchTaskState.logConfigId !== configId) return;
        if (!Array.isArray(executions)) { executions = []; }
        if (select) {
            select.innerHTML = executions.map(function(e) {
                var label = (e.startedAt ? formatDateTime(e.startedAt) : "") + " | " + statusLabel(e.status) + " | " + (e.triggerType || "");
                return '<option value="' + e.executionId + '">' + escapeHtml(label) + '</option>';
            }).join("");
        }
        var targetId = executionId || (executions.length > 0 ? executions[0].executionId : null);
        if (targetId) {
            if (select) select.value = String(targetId);
            batchTaskState.logExecutionId = targetId;
            loadBatchLogDetail(configId, targetId);
        } else {
            clearBatchLogDisplay();
        }
    } catch (e) {
        if (batchTaskState.logConfigId !== configId) return;
        if (select) select.innerHTML = '<option value="">加载失败</option>';
        console.error("Failed to load log executions", e);
    }
}

async function loadBatchLogDetail(configId, executionId) {
    if (!executionId) return;
    try {
        var detail = await api("/api/mail/batch-send/configs/" + configId + "/executions/" + executionId);
        renderBatchExecutionDetail(detail);
        if (detail.status === "RUNNING") {
            clearBatchLogRefreshTimer();
            batchTaskState.logRefreshTimer = setInterval(function() {
                if (batchTaskState.logConfigId === configId && batchTaskState.logExecutionId === executionId) {
                    loadBatchLogDetail(configId, executionId);
                }
            }, 3000);
        } else {
            clearBatchLogRefreshTimer();
        }
    } catch (e) {
        console.error("Failed to load log detail", e);
        var metrics = document.getElementById("batchLogMetrics");
        if (metrics) metrics.innerHTML = '<span class="muted">加载失败: ' + escapeHtml(e.message) + '</span>';
    }
}

function renderBatchExecutionDetail(d) {
    renderOutcomeMetrics(d);
    renderIntegrityWarning(d);
    renderReasons("batchLogFailureReasons", d.failureReasons, "无失败原因");
    renderReasons("batchLogSkippedReasons", d.skippedReasons, "无跳过原因");
    renderErrorSamples(d.errorSamples);
    renderBatchTimeline(d.progressBatches);
    renderLogStatusInfo(d);
}

function renderOutcomeMetrics(d) {
    var container = document.getElementById("batchLogMetrics");
    if (!container) return;
    var duration = d.durationMs != null ? formatDuration(d.durationMs) : "—";
    var items = [
        { label: "目标", value: String(d.target), cls: "" },
        { label: "成功", value: String(d.success), cls: "is-success" },
        { label: "失败", value: String(d.failure), cls: "is-failure" },
        { label: "跳过", value: String(d.skipped), cls: "is-skipped" },
        { label: "耗时", value: duration, cls: "" }
    ];
    if (d.remaining > 0) {
        items.push({ label: "剩余", value: String(d.remaining), cls: "" });
    }
    container.innerHTML = items.map(function(it) {
        return '<div class="batch-log-metric ' + it.cls + '">' +
            '<div class="batch-log-metric-label">' + escapeHtml(it.label) + '</div>' +
            '<div class="batch-log-metric-value">' + escapeHtml(it.value) + '</div>' +
            '</div>';
    }).join("");
}

function renderIntegrityWarning(d) {
    var el = document.getElementById("batchLogIntegrityWarning");
    if (!el) return;
    var expected = d.success + d.failure + d.skipped + (d.remaining || 0);
    if (expected !== d.target) {
        el.hidden = false;
        el.textContent = "统计待核对：目标 " + d.target + "，但成功+失败+跳过+剩余=" + expected;
    } else {
        el.hidden = true;
    }
}

function renderReasons(containerId, reasons, emptyText) {
    var container = document.getElementById(containerId);
    if (!container) return;
    if (!reasons || Object.keys(reasons).length === 0) {
        container.innerHTML = '<div class="batch-reason-row"><span style="color:#94a3b8;">' + emptyText + '</span></div>';
        return;
    }
    var entries = Object.values(reasons).sort(function(a, b) { return b.count - a.count; });
    container.innerHTML = entries.map(function(r) {
        return '<div class="batch-reason-row"><span>' + escapeHtml(r.label || "") + '</span><span class="batch-reason-count">' + escapeHtml(String(r.count)) + '</span></div>';
    }).join("");
}

function renderErrorSamples(samples) {
    var container = document.getElementById("batchLogErrorSampleList");
    if (!container) return;
    if (!Array.isArray(samples) || samples.length === 0) {
        container.innerHTML = '<span style="color:#94a3b8;font-size:12px;">无错误样例</span>';
        return;
    }
    container.innerHTML = samples.map(function(s) {
        return '<div style="padding:6px 0;font-size:12px;color:#475569;border-bottom:1px solid rgba(15,23,42,.04);">' + escapeHtml(s.substring(0, 200)) + '</div>';
    }).join("");
}

function renderLogStatusInfo(d) {
    var el = document.getElementById("batchLogStatusInfo");
    if (!el) return;
    var status = statusLabel(d.status);
    var trigger = triggerTypeLabel(d.triggerType);
    var time = d.finishedAt ? "完成于 " + formatDateTime(d.finishedAt) : (d.startedAt ? "开始于 " + formatDateTime(d.startedAt) : "");
    el.textContent = status + " | " + trigger + " | " + time;
}

function clearBatchLogDisplay() {
    var metrics = document.getElementById("batchLogMetrics");
    if (metrics) metrics.innerHTML = '<span class="muted">暂无执行记录</span>';
    var failureReasons = document.getElementById("batchLogFailureReasons");
    if (failureReasons) failureReasons.innerHTML = '';
    var skippedReasons = document.getElementById("batchLogSkippedReasons");
    if (skippedReasons) skippedReasons.innerHTML = '';
    var errorSamples = document.getElementById("batchLogErrorSampleList");
    if (errorSamples) errorSamples.innerHTML = '';
    var timeline = document.getElementById("batchLogTimeline");
    if (timeline) timeline.innerHTML = '';
    var statusInfo = document.getElementById("batchLogStatusInfo");
    if (statusInfo) statusInfo.textContent = '';
    var integrityWarning = document.getElementById("batchLogIntegrityWarning");
    if (integrityWarning) integrityWarning.hidden = true;
}

function renderBatchTimeline(batches) {
    var container = document.getElementById("batchLogTimeline");
    if (!container) return;
    if (!Array.isArray(batches) || batches.length === 0) {
        container.innerHTML = '<div class="batch-timeline-row"><span style="color:#94a3b8;">无批次记录</span></div>';
        return;
    }
    container.innerHTML = batches.map(function(b) {
        var time = b.updatedAt ? formatDateTime(b.updatedAt) : (b.startedAt ? formatDateTime(b.startedAt) : "—");
        return '<div class="batch-timeline-row">' +
            '<span class="batch-timeline-batch">批次 #' + b.batchNumber + '</span>' +
            '<span class="batch-timeline-time">' + escapeHtml(time) + '</span>' +
            '<span class="batch-timeline-status">' + escapeHtml(statusLabel(b.status || "")) + '</span>' +
            '<span class="batch-timeline-count">已处理 ' + (b.batchProcessed || 0) + '</span>' +
            '</div>';
    }).join("");
}

function statusLabel(s) {
    if (s === "RUNNING") return "运行中";
    if (s === "COMPLETED" || s === "SUCCESS") return "已完成";
    if (s === "FAILED") return "失败";
    if (s === "CANCELLED") return "已取消";
    return s || "—";
}

function triggerTypeLabel(t) {
    if (t === "SCHEDULED") return "定时";
    if (t === "MANUAL") return "手动";
    if (t === "CRON") return "Cron";
    return t || "—";
}

function formatDateTime(dt) {
    if (!dt) return "—";
    try {
        var d = new Date(dt);
        return d.toLocaleString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" });
    } catch (e) { return String(dt); }
}

function formatDuration(ms) {
    if (ms == null) return "—";
    var seconds = Math.floor(ms / 1000);
    if (seconds < 60) return seconds + "秒";
    var minutes = Math.floor(seconds / 60);
    var remainSec = seconds % 60;
    if (minutes < 60) return minutes + "分" + remainSec + "秒";
    var hours = Math.floor(minutes / 60);
    var remainMin = minutes % 60;
    return hours + "时" + remainMin + "分";
}

// ── Event Bindings (called once after DOM ready) ─────────────────────────────────────

function bindBatchSendTaskEvents() {
    // Tab switching
    $$(".batch-send-tab").forEach(function(tab) {
        tab.addEventListener("click", function() {
            switchBatchSendTab(tab.dataset.tab);
        });
    });

    // Close modal
    var closeBtn = document.querySelector(".batch-send-close-btn");
    if (closeBtn) closeBtn.addEventListener("click", closeBatchSendTaskModal);

    // Search input
    var searchInput = document.getElementById("batchConfigSearch");
    if (searchInput) searchInput.addEventListener("input", handleBatchConfigSearchInput);

    // Create new config button
    var createBtn = document.getElementById("batchConfigCreateBtn");
    if (createBtn) createBtn.addEventListener("click", showBatchConfigEditorForm);

    // Config editor buttons
    var editorCancelBtn = document.getElementById("batchConfigEditorCancelBtn");
    if (editorCancelBtn) editorCancelBtn.addEventListener("click", hideBatchConfigEditor);
    var editorSaveBtn = document.getElementById("batchConfigEditorSaveBtn");
    if (editorSaveBtn) editorSaveBtn.addEventListener("click", saveBatchConfigEditor);

    // Frequency change -> time field visibility
    var freqSelect = document.getElementById("batchConfigEditorFrequency");
    if (freqSelect) {
        freqSelect.addEventListener("change", function() {
            var timeField = document.getElementById("batchConfigEditorTimeField");
            if (timeField) timeField.style.display = freqSelect.value === "hourly" ? "none" : "";
        });
    }

    bindBatchTagPicker("batchConfigEditorTags");
    bindBatchTagPicker("batchManualTags");

    // Manual source search — autocomplete
    var sourceQuery = document.getElementById("batchManualSourceQuery");
    if (sourceQuery) {
        sourceQuery.addEventListener("focus", function() {
            loadBatchManualSourceOptions("");
        });
        sourceQuery.addEventListener("input", function() {
            handleManualSourceSearch();
        });
        sourceQuery.addEventListener("keydown", function(event) {
            if (event.key === "Escape") closeBatchManualSourceDropdown();
        });
        sourceQuery.addEventListener("blur", function() {
            setTimeout(function() {
                if (!document.querySelector(".batch-manual-source-dropdown-item:hover")) {
                    closeBatchManualSourceDropdown();
                }
            }, 150);
        });
    }

    // Clear source
    var clearSourceBtn = document.getElementById("batchManualClearSourceBtn");
    if (clearSourceBtn) clearSourceBtn.addEventListener("click", clearManualSource);

    // Manual form diff detection
    var manualInputs = document.querySelectorAll("#batchManualPanel input, #batchManualPanel select");
    manualInputs.forEach(function(input) {
        input.addEventListener("input", computeAndRenderDiffs);
        input.addEventListener("change", function() {
            var v = readManualFormValues();
            if (!batchTaskState.manualDraft) batchTaskState.manualDraft = {};
            Object.assign(batchTaskState.manualDraft, v);
            computeAndRenderDiffs();
        });
    });

    // Execute button
    var executeBtn = document.getElementById("batchManualExecuteBtn");
    if (executeBtn) executeBtn.addEventListener("click", handleManualExecute);

    // Log drawer
    var logCloseBtn = document.getElementById("batchLogDrawerCloseBtn");
    if (logCloseBtn) logCloseBtn.addEventListener("click", closeBatchLogDrawer);

    var logExecSelect = document.getElementById("batchLogExecutionSelect");
    if (logExecSelect) {
        logExecSelect.addEventListener("change", function() {
            var executionId = logExecSelect.value ? Number(logExecSelect.value) : null;
            if (executionId && batchTaskState.logConfigId) {
                batchTaskState.logExecutionId = executionId;
                loadBatchLogDetail(batchTaskState.logConfigId, executionId);
            }
        });
    }

    // Confirm dialog
    var confirmCloseBtn = document.getElementById("batchManualConfirmCloseBtn");
    if (confirmCloseBtn) confirmCloseBtn.addEventListener("click", closeBatchManualConfirmDialog);
    var confirmCancelBtn = document.getElementById("batchManualConfirmCancelBtn");
    if (confirmCancelBtn) confirmCancelBtn.addEventListener("click", closeBatchManualConfirmDialog);
    var confirmOkBtn = document.getElementById("batchManualConfirmOkBtn");
    if (confirmOkBtn) confirmOkBtn.addEventListener("click", confirmManualExecution);

    // Close on overlay click
    var modal = document.getElementById("batchSendTaskModal");
    if (modal) {
        modal.addEventListener("click", function(e) {
            if (e.target === modal) closeBatchSendTaskModal();
        });
    }
    var confirmDialog = document.getElementById("batchManualConfirmDialog");
    if (confirmDialog) {
        confirmDialog.addEventListener("click", function(e) {
            if (e.target === confirmDialog) closeBatchManualConfirmDialog();
        });
    }
}

// Auto-init on load: bind events after DOM is ready
document.addEventListener("DOMContentLoaded", function() {
    bindBatchSendTaskEvents();
});

bootstrap();
