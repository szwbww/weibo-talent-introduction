const state = {
    view: "accounts",
    accounts: [],
    categories: [],
    qaRules: [],
    contacts: [],
    contactsPage: 0,
    contactsTotalHits: 0,
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
    revalidateBtn: "重新验证候选人",
    promoteRawBtn: "扫描 RAW 可晋升",
    discoverBtn: "发现专家"
};

const taskButtonMapping = {
    EXPERT_REVALIDATION: { label: "重新验证候选人", btnId: "revalidateBtn" },
    RAW_PROMOTION_SCAN: { label: "扫描 RAW 可晋升", btnId: "promoteRawBtn" },
    EXPERT_DISCOVERY: { label: "发现专家", btnId: "discoverBtn" },
    MANUAL_INITIAL_OUTREACH: { label: "批量发送介绍邮件", btnId: "bulkOutreachBtn" }
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
    for (const taskType of Object.keys(taskButtonMapping)) {
        try {
            const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
            await handleAuthResponse(response);
            if (response.status === 204 || !response.ok) continue;
            const progress = await response.json();
            if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
                if (taskType === "MANUAL_INITIAL_OUTREACH") {
                    setOutreachButtonRunning();
                    updateOutreachProgressPanel(progress);
                } else {
                    const mapping = taskButtonMapping[taskType];
                    if (mapping) setTaskButtonRunning(mapping.btnId);
                }
                startTaskWatcher(taskType);
            }
        } catch (e) { /* 静默 */ }
    }
}

// taskModalGenerationSequence, createTaskModalContext, currentTaskModal,
// isProgressTerminal, isExecutionTerminal, isCurrentTaskModal, bindTaskModalExecution,
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
            if (taskType === "MANUAL_INITIAL_OUTREACH") {
                stopTaskWatcher(taskType, true, watcher);
                restoreOutreachButton();
                return;
            }
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

        if (taskType === "MANUAL_INITIAL_OUTREACH") {
            updateOutreachProgressPanel(progress);
        }

        if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
            watcher.awaitingLaunch = false;
            watcher.observedActive = true;
            if (taskType === "MANUAL_INITIAL_OUTREACH") {
                setOutreachButtonRunning();
            }
            return;
        }

        if (isProgressTerminal(progress.status)) {
            stopTaskWatcher(taskType, true, watcher);
            if (taskType === "MANUAL_INITIAL_OUTREACH") {
                restoreOutreachButton();
                loadContacts();
                const verb = progress.status === "CANCELLED" ? "已取消"
                           : progress.status === "COMPLETED" ? "已完成" : "已结束";
                notifyTaskCompletionOnce({
                    taskType,
                    executionId: progress.executionId,
                    status: progress.status,
                    message: `批量发送介绍邮件 ${verb}`,
                    level: progress.status === "FAILED" ? "error" : "ok"
                });
            } else {
                const mapping = taskButtonMapping[taskType];
                const label = mapping?.label || taskType;
                const verb = progress.status === "CANCELLED" ? "已取消"
                           : progress.status === "COMPLETED" ? "已完成" : "已结束";
                notifyTaskCompletionOnce({
                    taskType,
                    executionId: progress.executionId,
                    status: progress.status,
                    message: `${label} ${verb}`,
                    level: progress.status === "FAILED" ? "error" : "ok"
                });
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
            cancelBtn.textContent = "取消任务";
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
    runBody.innerHTML = runs.length > 0
        ? runs.map(r => renderRunRow(r, taskType)).join("")
        : `<tr><td colspan="8" class="muted" style="text-align:center;padding:12px;">暂无执行记录</td></tr>`;
    if (currentTaskModal) {
        runs.forEach(r => {
            currentTaskModal.runStatusByExecutionId[r.executionId] = r.status;
        });
    }
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

    if (currentTaskModal) {
        const justObservedTerminal = observeTaskModalProgress(progress, generation);
        if (justObservedTerminal) {
            cancelBtn.disabled = true;
            cancelBtn.textContent = progress.status === "COMPLETED" ? "已完成" : (progress.status === "CANCELLED" ? "已取消" : "失败");
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
        if (progress.status === "COMPLETED") bar.className = "task-progress-bar completed";
        if (progress.status === "FAILED" || progress.status === "CANCELLED") bar.className = "task-progress-bar failed";
    }
}

function updateTaskModalLogs(executionId, logs) {
    // 刷新第一层执行行的内嵌批次表
    const batchBody = document.getElementById(`batch-body-${executionId}`);
    if (!batchBody) return;
    batchBody.innerHTML = renderBatchTable(logs);
}

function renderBatchTable(logs) {
    if (!logs || logs.length === 0) {
        return `<tr><td colspan="6" class="muted" style="text-align:center;padding:12px;">暂无批次日志</td></tr>`;
    }
    return logs.map(log => {
        const time = formatDateTime(log.createdAt);
        const pct = log.totalCount > 0 ? Math.round((log.processedCount * 100) / log.totalCount) + "%" : "";
        return `
            <tr>
                <td>${log.batchNumber}</td>
                <td>${log.batchProcessed}</td>
                <td>${log.batchPassed}</td>
                <td>${log.batchRejected}</td>
                <td>${log.processedCount}/${log.totalCount} ${pct}</td>
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
                                <th style="padding:6px;">时间</th>
                            </tr>
                        </thead>
                        <tbody id="batch-body-${executionId}">
                            <tr><td colspan="6" class="muted" style="text-align:center;padding:12px;">加载中...</td></tr>
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
    $$(".nav-tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.view === view));
    $$(".view").forEach((section) => section.classList.toggle("active", section.id === `view-${view}`));
    $("#viewTitle").textContent = viewMeta[view][0];
    $("#viewSubtitle").textContent = viewMeta[view][1];
    refreshCurrentView();
    if (view === "contacts") {
        resumeProgressPollingIfNeeded();
    } else {
        ["EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY"].forEach(stopProgressPollingFor);
    }
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

    const formSelect = $("#qaRuleForm").categoryId;
    const formSelectValue = formSelect.value;
    formSelect.innerHTML = `<option value="">请选择分类</option>` + categories.map((category) => `
        <option value="${category.id}">${escapeHtml(category.categoryName)}</option>
    `).join("");
    formSelect.value = formSelectValue;

    renderQaRulesTable();
}

function renderQaRulesTable() {
    $("#qaRulesTable").innerHTML = state.qaRules.map((rule) => {
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

const expertTagLabels = {
    auto_promoted: "自动晋升",
    verified: "已验证",
    discovered: "新发现"
};

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

async function loadContacts() {
    const level = $("#expertIndexLevel").value;
    const size = Number($("#expertIndexSize").value || "50");
    const operatorStatus = $("#contactStatusFilter")?.value || "";
    const needsAttention = $("#contactNeedsAttentionFilter")?.value || "";
    let tag = $("#expertTagFilter")?.value || "";
    renderContactListSkeleton();

    const tagFilterEl = $("#expertTagFilter");
    if (operatorStatus || needsAttention) {
        tag = "";
        if (tagFilterEl) {
            tagFilterEl.value = "";
            tagFilterEl.disabled = true;
            tagFilterEl.parentElement.style.opacity = "0.5";
            tagFilterEl.parentElement.title = "标签筛选仅在 ES 查询模式下可用";
        }
    } else {
        if (tagFilterEl) {
            tagFilterEl.disabled = false;
            tagFilterEl.parentElement.style.opacity = "1";
            tagFilterEl.parentElement.title = "";
        }
    }

    let contacts = [];
    let totalHits = 0;
    try {
    if (operatorStatus || needsAttention) {
        const params = new URLSearchParams();
        if (operatorStatus) params.set("operatorStatus", operatorStatus);
        if (needsAttention) params.set("needsAttention", needsAttention);
        const data = await api(`/api/expert-contacts?${params}`);
        let rawContacts = data.contacts || data;
        totalHits = data.totalCount ?? rawContacts.length;
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
            updatedAt: c.updatedAt || null
        }));
    } else {
        const params = new URLSearchParams();
        params.set("level", level);
        params.set("size", size);
        params.set("from", state.contactsPage * size);
        if (tag) params.set("tag", tag);
        const sortBy = $("#expertSortBy")?.value || "";
        if (sortBy) params.set("sortBy", sortBy);
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
            updatedAt: e.updatedAt || null
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
    if ((operatorStatus || needsAttention) && sortBy === "updatedAt") {
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

    if (contacts.length === 0) {
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
                ${contact.employment || tagsHtml ? `
                <div class="expert-row-sub">
                    ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
                    ${tagsHtml ? `<span class="expert-row-tags">${tagsHtml}</span>` : ""}
                </div>` : ""}
            </div>
        </div>
        `;
    }).join("");
    refreshAutoReplySummary().catch(() => {});
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
    const checked = $$(".expert-select-cb:checked")
        .map(cb => Number(cb.dataset.contactId))
        .filter(id => id > 0);

    if (checked.length === 0) {
        const confirmed = confirm("未勾选专家，将检查所有已联系专家的回复，继续？");
        if (!confirmed) return;
    }

    await executeCheckReplies(checked);
}

async function executeCheckReplies(checked = []) {
    const payload = checked.length > 0
        ? { contactIds: checked }
        : {};

    const btn = $("#checkRepliesBtn");
    btn.disabled = true;
    const originalText = btn.textContent;
    btn.textContent = "检查中...";

    try {
        await api("/api/mail/auto-reply/check-replies", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        showStatus(checked.length > 0
            ? `检查完成: 已检查 ${checked.length} 位指定专家的回复`
            : "检查完成: 已检查所有已联系专家的回复", "ok");
    } catch (e) {
        showStatus("检查回复失败: " + e.message, "error");
        return;
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }

    try {
        await showPollLog();
    } catch (e) {
        showStatus("检查已完成，但轮询日志加载失败: " + e.message, "error");
    }
}

// ---- 操作确认弹窗 ----
const taskLaunchConfigs = {
    EXPERT_REVALIDATION: {
        title: "重新验证候选人",
        desc: "将扫描所有 CANDIDATE 层专家，不符合条件的将被降级回 RAW。",
        btnId: "revalidateBtn",
        showKeyword: false,
        showMaxPromotions: false,
        run: executeRevalidate
    },
    RAW_PROMOTION_SCAN: {
        title: "扫描 RAW 可晋升",
        desc: "将扫描 RAW 层专家，符合筛选条件的将被晋升到 CANDIDATE 层。",
        btnId: "promoteRawBtn",
        showKeyword: false,
        showMaxPromotions: true,
        run: executePromoteRaw
    },
    EXPERT_DISCOVERY: {
        title: "发现专家",
        desc: "从外部数据源搜索并导入新专家到系统中。",
        btnId: "discoverBtn",
        showKeyword: true,
        showMaxPromotions: false,
        run: executeDiscover
    }
};

function openTaskLaunchModal(taskType) {
    const config = taskLaunchConfigs[taskType];
    if (!config) return;

    const modal = $("#taskProgressModal");
    $("#taskModalTitle").textContent = config.title;

    // Show config section, hide progress section
    $("#taskModalConfigSection").hidden = false;
    $("#taskModalProgressSection").hidden = true;

    $("#taskLaunchDesc").textContent = config.desc;
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
    $("#taskModalBySource").hidden = true;

    const runBtn = $("#taskLaunchRunBtn");
    runBtn.onclick = () => {
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

async function handleRevalidateCandidates() {
    const taskType = "EXPERT_REVALIDATION";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "重新验证候选人", "revalidateBtn", { knownActiveAtOpen: true });
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
    openTaskModal(taskType, "重新验证候选人", "revalidateBtn", { launchRequested: true });
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
        openTaskModal(taskType, "扫描 RAW 可晋升", "promoteRawBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}

async function executePromoteRaw() {
    const taskType = "RAW_PROMOTION_SCAN";
    const maxPromotions = parseInt($("#taskLaunchMaxPromotions")?.value) || 1000;
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "扫描 RAW 可晋升", "promoteRawBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api(`/api/experts/promote-eligible-raw?maxPromotions=${maxPromotions}`, { method: "POST" });
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
        notifyTaskCompletionOnce({
            taskType,
            executionId: response.executionId,
            status: "COMPLETED",
            message: `RAW 晋升扫描完成: 总数 ${stats.total}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}, 邮箱拒收 ${stats.emailRejected}${failureMsg}`,
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

async function handleDiscover() {
    const taskType = "EXPERT_DISCOVERY";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "发现专家", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
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
    openTaskModal(taskType, "发现专家", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const selectedSources = getSelectedSources();
        let response;
        if (keywords) {
            const params = new URLSearchParams();
            keywords.split(",").map(k => k.trim()).filter(k => k).forEach(k => params.append("keywords", k));
            if (selectedSources.length > 0) selectedSources.forEach(s => params.append("sources", s));
            response = await api(`/api/expert-discovery/run/by-keyword?${params}`, { method: "POST" });
        } else {
            const body = selectedSources.length > 0 ? { sources: selectedSources } : {};
            response = await api("/api/expert-discovery/run", { method: "POST", body: JSON.stringify(body), headers: { "Content-Type": "application/json" } });
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

function showExpertDetail(expert) {
    const name = expert.displayName || expert.email || expert.orcidId || "?";
    const initial = name.charAt(0).toUpperCase();
    const contactDetail = $("#contactDetail");
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
                    ${renderExpertDocuments(documents)}
                </div>

                <div class="metadata-card span-all" id="expertOperatorLogsSection">
                    ${renderOperatorLogs(logs)}
                </div>
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

function renderExpertDocuments(documents) {
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
        <div class="metadata-card-header">
            <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <span>专家上传资料</span>
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
    if (action === "preview-document") {
        const previewUrl = element.dataset.url;
        if (previewUrl) window.open(`${contextPath}${previewUrl}`, "_blank");
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
        return `<div>
            <a href="javascript:void 0" data-action="open-contact-from-unmatched" data-id="${record.expertContactId}">
            ${escapeHtml(record.expertName || "(未命名)")}</a>
            <div class="text-muted">${operatorStatusLabels[record.expertOperatorStatus] || labelStatus(record.expertCurrentStatus) || "-"}</div>
            <div class="text-muted">${indexLevelLabels[record.expertIndexLevel] || record.expertIndexLevel || "-"}</div>
        </div>`;
    }
    return `<span class="text-muted">未匹配</span>`;
}

function renderUnmatchedActions(r) {
    const actions = [];
    actions.push(`<button class="button" data-action="view-unmatched" data-id="${r.id}">查看/处理</button>`);
    if (r.expertContactId) {
        actions.push(`<button class="button" data-action="open-contact-from-unmatched" data-id="${r.expertContactId}">查看专家</button>`);
    }
    actions.push(`<button class="button" data-action="mark-unmatched-resolved" data-id="${r.id}">标记已处理</button>`);
    return actions.join(" ");
}

async function showUnmatchedDetail(id) {
    const [data, options, logs] = await Promise.all([
        api(`/api/mail/unmatched-inbound/${id}`),
        loadMailSendOptions(),
        api(`/api/operator-action-logs?inboundProcessingId=${id}&pageSize=50&pageOffset=0`).catch(() => ({ records: [] }))
    ]);
    const record = data.record;
    const candidates = data.candidates || [];
    const contact = data.contact;
    const panel = $("#unmatchedDetailPanel");
    panel.hidden = false;

    const linkedExpertHtml = record.expertContactId && contact ? `
        <div class="detail-section">
            <h3>关联专家</h3>
            <div class="linked-expert-card">
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
            <div class="detail-section" style="margin-top:12px;">
                <h3>变更专家状态</h3>
                <div style="display:flex;gap:8px;align-items:center;">
                    <select id="unmatchedOperatorStatusSelect" data-record-id="${id}" style="flex:1;">
                        ${optionsFromArray(operatorStatusOptions, false, "请选择", contact.operatorStatus || "")}
                    </select>
                    <button class="button" data-action="change-operator-status" data-record-id="${id}">确认变更</button>
                </div>
            </div>
            <div class="detail-section" style="margin-top:12px;">
                <h3>变更专家层级</h3>
                <div style="display:flex;gap:8px;align-items:center;">
                    <select id="unmatchedIndexLevelSelect" data-record-id="${id}" style="flex:1;">
                        ${optionsFromArray(indexLevelOptions, false, "请选择", contact.currentIndexLevel || "")}
                    </select>
                    <button class="button" data-action="change-index-level" data-record-id="${id}">确认变更</button>
                </div>
            </div>
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

    const qaOptions = options.filter(o => o.optionType === "QA");
    const qaReplyHtml = qaOptions.length > 0 ? `
        <div class="detail-section" style="margin-top:12px;">
            <h3>QA 邮件回复</h3>
            <div style="display:flex;gap:8px;align-items:center;">
                <select id="unmatchedQaOption" style="flex:1;">
                    ${qaOptions.map(o => `
                        <option value="${escapeHtml(o.optionValue)}">${escapeHtml(o.optionName)}${o.subject ? ` - ${escapeHtml(o.subject)}` : ""}</option>
                    `).join("")}
                </select>
                <button class="button primary" data-action="send-pending-qa-reply" data-record-id="${id}">发送 QA 邮件</button>
            </div>
        </div>
    ` : "";

    panel.innerHTML = `
        <div class="panel-head">
            <h2>来信详情与处理</h2>
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

            ${linkedExpertHtml}

            ${qaReplyHtml}

            <div class="detail-section" style="margin-top:12px;">
                <h3>人工富文本回复</h3>
                <input id="manualReplySubject" placeholder="邮件主题" style="margin-bottom:8px;">
                <div class="rich-toolbar">
                    <button type="button" data-action="rich-command" data-command="bold"><strong>B</strong></button>
                    <button type="button" data-action="rich-command" data-command="italic"><em>I</em></button>
                    <button type="button" data-action="rich-command" data-command="insertUnorderedList">列表</button>
                    <button type="button" data-action="rich-command" data-command="createLink">链接</button>
                </div>
                <div id="manualRichReplyEditor" contenteditable="true" class="rich-editor"></div>
                <button class="button primary" data-action="send-manual-rich-reply" data-record-id="${id}" style="margin-top:8px;">发送人工回复</button>
            </div>

            <div class="detail-section" style="margin-top:12px;">
                <h3>操作日志</h3>
                ${renderOperatorLogs(logs)}
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
    if (action === "send-pending-qa-reply") {
        const optionValue = $("#unmatchedQaOption")?.value;
        if (!optionValue) {
            showStatus("请选择 QA 回复选项", "error");
            return;
        }
        const operatorName = window.localStorage.getItem("operatorName") || "console";
        try {
            await api(`/api/mail/unmatched-inbound/${id}/qa-reply`, {
                method: "POST",
                body: JSON.stringify({ qaRuleId: Number(optionValue), operatorName })
            });
            alert("QA 邮件发送成功");
        } catch (e) {
            alert("QA 邮件发送失败: " + e.message);
            return;
        }
        await showUnmatchedDetail(id);
        await loadUnmatched();
        return;
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
        const operatorName = window.localStorage.getItem("operatorName") || "console";
        try {
            await api(`/api/mail/unmatched-inbound/${id}/manual-rich-reply`, {
                method: "POST",
                body: JSON.stringify({
                    senderAccountCode: null,
                    subject,
                    htmlBody: editor.innerHTML,
                    textBody: editor.innerText,
                    operatorName
                })
            });
            alert("人工回复邮件发送成功");
        } catch (e) {
            alert("人工回复发送失败: " + e.message);
            return;
        }
        await showUnmatchedDetail(id);
        await loadUnmatched();
        return;
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
    $("#qaRuleForm").addEventListener("submit", (event) => saveQaRule(event).catch((error) => showStatus(error.message, "error")));
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
    $("#loadContactsBtn").addEventListener("click", () => {
        loadContacts().catch((e) => showStatus(e.message, "error"));
    });
    $("#contactList").addEventListener("click", (event) => {
        const item = event.target.closest("[data-action]");
        if (item) handleContactAction(item).catch((error) => showStatus(error.message, "error"));
    });
    $("#contactDetail").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleContactAction(button).catch((error) => showStatus(error.message, "error"));
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
    const updateFilterBadge = () => {
        const active = [
            $("#expertSortBy").value !== "",
            $("#expertIndexLevel").value !== "CANDIDATE",
            $("#expertIndexSize").value !== "50",
            $("#contactStatusFilter").value !== "",
            $("#contactNeedsAttentionFilter").value !== "",
            $("#expertTagFilter").value !== ""
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
    ["expertIndexLevel", "expertIndexSize", "contactNeedsAttentionFilter",
        "contactStatusFilter", "expertTagFilter", "expertSortBy"].forEach((id) => {
        $(`#${id}`).addEventListener("change", reloadContactsFromStart);
    });
    updateFilterBadge();
    $("#filterToggleBtn").addEventListener("click", () => {
        const group = $("#contactsFilterGroup");
        const open = group.classList.toggle("open");
        $("#filterToggleBtn").setAttribute("aria-expanded", String(open));
    });
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

    // 后台任务下拉菜单
    const taskMenuToggle = $("#taskMenuToggle");
    const taskMenu = $("#taskMenu");
    if (taskMenuToggle && taskMenu) {
        taskMenuToggle.addEventListener("click", (event) => {
            event.stopPropagation();
            taskMenu.hidden = !taskMenu.hidden;
            taskMenuToggle.setAttribute("aria-expanded", String(!taskMenu.hidden));
        });
        document.addEventListener("click", (event) => {
            if (!taskMenu.hidden && !event.target.closest("#taskMenuDropdown")) {
                taskMenu.hidden = true;
                taskMenuToggle.setAttribute("aria-expanded", "false");
            }
        });
        taskMenu.addEventListener("click", (event) => {
            if (event.target.closest(".dropdown-item")) {
                taskMenu.hidden = true;
                taskMenuToggle.setAttribute("aria-expanded", "false");
            }
        });
    }

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
                if (progress.status === "RUNNING" || progress.status === "CANCELLING" ||
                    progress.status === "COMPLETED" || progress.status === "FAILED" || progress.status === "CANCELLED") {
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
        const total = summary.total;
        const enabled = summary.enabled;
        const disabled = summary.disabled;
        if (total === 0) {
            btn.textContent = "自动回复：无专家";
            btn.disabled = true;
        } else if (enabled === total) {
            btn.textContent = "自动回复：全部开启 ✓（点击全部关闭）";
            btn.disabled = false;
        } else if (disabled === total) {
            btn.textContent = "自动回复：全部关闭（点击全部开启）";
            btn.disabled = false;
        } else {
            btn.textContent = `自动回复：部分开启 ${enabled}/${total}（点击全部开启）`;
            btn.disabled = false;
        }
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
        const total = lastAutoReplySummary.total;
        const enabled = lastAutoReplySummary.enabled;
        const handoffLocked = lastAutoReplySummary.handoffLocked;

        let targetEnabled = true;
        let confirmMsg = "";

        if (enabled === total) {
            targetEnabled = false;
            confirmMsg = "是否确认关闭所有专家的自动回复？";
        } else {
            targetEnabled = true;
            if (handoffLocked > 0) {
                confirmMsg = `是否确认开启所有专家的自动回复？\n\n注意：将跳过 ${handoffLocked} 位人工接管中（需要人工处理）的专家。`;
            } else {
                confirmMsg = "是否确认开启所有专家的自动回复？";
            }
        }

        const confirmed = confirm(confirmMsg);
        if (!confirmed) return;

        const operatorName = getConfiguredOperatorName();
        if (!operatorName) {
            showStatus("请先设置操作员姓名", "error");
            return;
        }

        btn.disabled = true;
        btn.textContent = "正在更新...";

        try {
            const res = await api("/api/expert-contacts/auto-reply/bulk", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ enabled: targetEnabled, operatorName })
            });
            showStatus(`已更新 ${res.updated} 位专家的自动回复设置` + (res.skipped ? `，跳过 ${res.skipped} 位` : ""), "ok");
        } catch (e) {
            showStatus("更新失败: " + e.message, "error");
        } finally {
            await refreshAutoReplySummary();
            await loadContacts();
        }
    });
}

function initManualOutreach() {
    const btn = $("#bulkOutreachBtn");
    if (!btn) return;

    btn.addEventListener("click", async () => {
        if (btn.classList.contains("danger")) {
            const confirmed = confirm("是否确认停止批量发送介绍邮件？");
            if (!confirmed) return;
            btn.disabled = true;
            btn.textContent = "停止中...";
            try {
                await api(`/api/task-progress/MANUAL_INITIAL_OUTREACH/cancel`, { method: "POST" });
                showStatus("已发送停止请求", "ok");
            } catch (e) {
                showStatus("停止发送失败: " + e.message, "error");
                btn.disabled = false;
                btn.textContent = "停止发送";
            }
            return;
        }

        btn.disabled = true;
        try {
            const countRes = await api("/api/mail/manual-outreach/pending-count");
            const pendingSummary = summarizeManualOutreachPending(countRes);
            if (pendingSummary.total === 0) {
                showStatus(pendingSummary.emptyMessage, "info");
                btn.disabled = false;
                return;
            }

            const confirmed = confirm(pendingSummary.confirmMessage);
            if (!confirmed) {
                btn.disabled = false;
                return;
            }

            await api("/api/mail/manual-outreach/start", { method: "POST" });
            showStatus("手动批量首发邮件已启动", "ok");

            const panel = $("#outreachProgressPanel");
            if (panel) {
                panel.hidden = false;
                $("#outreachCounters").textContent = `待发送 ${pendingSummary.total} · 已发送 0 · 失败 0`;
                $("#outreachProgressPercent").textContent = "0%";
                $("#outreachProgressFill").style.width = "0%";
                $("#outreachProgressDetail").textContent = "正在启动...";
                $("#outreachErrors").hidden = true;
                $("#outreachErrors").textContent = "";
            }

            setOutreachButtonRunning();
            startTaskWatcher("MANUAL_INITIAL_OUTREACH", { awaitingLaunch: true });
        } catch (e) {
            showStatus("启动失败: " + e.message, "error");
            btn.disabled = false;
        }
    });

    $("#closeOutreachProgressBtn")?.addEventListener("click", () => {
        $("#outreachProgressPanel").hidden = true;
    });
}

function summarizeManualOutreachPending(countRes) {
    const pending = Number(countRes?.pending || 0);
    const retryable = Number(countRes?.retryable || 0);
    const profileMissing = Number(countRes?.profileMissing || 0);
    const total = pending + retryable;
    const missingNote = profileMissing > 0
        ? `；另有 ${profileMissing} 位待补发专家因资料缺失将跳过`
        : "";
    return {
        pending,
        retryable,
        profileMissing,
        total,
        confirmMessage: `将向 ${total} 位专家发送介绍邮件（${pending} 位未联系，${retryable} 位上次失败待补发${missingNote}），是否开始？`,
        emptyMessage: profileMissing > 0
            ? `没有可发送的专家；${profileMissing} 位待补发专家因资料缺失已跳过`
            : "没有待发送的专家（没有满足条件的未联系候选人且无上次失败的专家）"
    };
}

function setOutreachButtonRunning() {
    const btn = $("#bulkOutreachBtn");
    if (!btn) return;
    btn.disabled = false;
    btn.textContent = "停止发送";
    btn.className = "button danger";
}

function restoreOutreachButton() {
    const btn = $("#bulkOutreachBtn");
    if (!btn) return;
    btn.disabled = false;
    btn.textContent = "批量发送介绍邮件";
    btn.className = "button primary";
}

function updateOutreachProgressPanel(progress) {
    const panel = $("#outreachProgressPanel");
    if (!panel) return;
    panel.hidden = false;

    const details = progress.details || {};
    const pending = details.pending || 0;
    const sent = details.sent || 0;
    const failed = details.failed || 0;
    const total = progress.totalCount || (pending + sent + failed);
    const percent = progress.percentage != null ? progress.percentage : (total > 0 ? Math.round((sent + failed) * 100 / total) : 0);

    $("#outreachCounters").textContent = `待发送 ${pending} · 已发送 ${sent} · 失败 ${failed}`;
    $("#outreachProgressPercent").textContent = `${percent}%`;
    $("#outreachProgressFill").style.width = `${percent}%`;
    $("#outreachProgressDetail").textContent = progress.message || "";

    const errors = progress.errors || [];
    const errorsDiv = $("#outreachErrors");
    if (errorsDiv) {
        if (errors.length > 0) {
            errorsDiv.hidden = false;
            errorsDiv.innerHTML = errors.map(err => `<div>${escapeHtml(err)}</div>`).join("");
        } else {
            errorsDiv.hidden = true;
        }
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
        updateUnmatchedBadge();
        resumeProgressPollingIfNeeded().catch(() => {});
        refreshCurrentView();
    }
}

function stopAuthenticatedApp() {
    appStarted = false;
    const shell = $(".app-shell");
    if (shell) {
        shell.style.display = "none";
    }
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

function bootstrap() {
    bindEvents();
    initManualOutreach();
    initBulkAutoReply();
    initPollLogPanel();
    initLayoutResizer();
    bindAuthEvents();
    checkAuth();
}

bootstrap();
