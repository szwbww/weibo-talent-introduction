var taskModalGenerationSequence = 0;
var currentTaskModal = null;
var taskCompletionNotifications = new Set();

function nextTaskModalGeneration() {
    taskModalGenerationSequence += 1;
    return taskModalGenerationSequence;
}

function createTaskModalContext(taskType, label, btnId, mode) {
    return {
        taskType,
        label,
        btnId,
        mode,
        generation: nextTaskModalGeneration(),
        progressTimer: null,
        logTimer: null,
        runListTimer: null,
        executionId: null,
        expandedExecutionId: null,
        lastProgressStatus: null,
        terminalObserved: false,
        terminalProgressSnapshot: null,
        terminalFinalizationStarted: false,
        terminalFinalizationPromise: null,
        terminalExecutionReconciled: false,
        terminalLogsSynchronized: false,
        terminalFinalized: false,
        terminalFinalizationFailed: false,
        launchRequested: false,
        knownActiveAtOpen: false,
        openedAt: Date.now(),
        executionBindingSource: null,
        batchLogsByExecutionId: {},
        runStatusByExecutionId: {}
    };
}

function isProgressTerminal(status) {
    return status === "COMPLETED" || status === "FAILED" || status === "CANCELLED";
}

function isExecutionTerminal(status) {
    return status !== "RUNNING" && status !== "CANCELLING";
}

function isCurrentTaskModal(taskType, generation) {
    return currentTaskModal != null
        && currentTaskModal.taskType === taskType
        && currentTaskModal.generation === generation;
}

async function bindTaskModalExecution(taskType, generation, executionId, options) {
    options = options || {};
    if (!isCurrentTaskModal(taskType, generation)) return false;
    currentTaskModal.executionId = executionId;
    currentTaskModal.expandedExecutionId = executionId;
    currentTaskModal.executionBindingSource = options.source || "LAUNCH_RESPONSE";
    if (currentTaskModal.terminalObserved) {
        await finalizeCurrentTaskModalTerminal(taskType, generation);
    } else if (options.refreshRunList !== false) {
        await fetchRunList(taskType, generation);
    }
    return true;
}

async function adoptTaskModalExecution(taskType, generation, executionId, source) {
    return bindTaskModalExecution(taskType, generation, executionId, {
        source,
        refreshRunList: false
    });
}

function selectExecutionForCurrentModal(runs, modal) {
    if (!runs || !modal || modal.executionId != null) return null;
    if (modal.mode !== "PROGRESS") return null;

    const activeRuns = runs
        .filter(run => run.status === "RUNNING" || run.status === "CANCELLING")
        .sort(compareRunsNewestFirst);
    if (activeRuns.length > 0) return activeRuns[0];

    if (modal.knownActiveAtOpen && modal.terminalObserved && runs.length > 0) {
        return runs.slice().sort(compareRunsNewestFirst)[0];
    }

    return null;
}

function compareRunsNewestFirst(a, b) {
    const aTime = Date.parse(a.startedAt || "") || 0;
    const bTime = Date.parse(b.startedAt || "") || 0;
    if (aTime !== bTime) return bTime - aTime;
    return (b.executionId || 0) - (a.executionId || 0);
}

async function fetchJsonForCurrentTaskModal(taskType, generation, url, options) {
    if (!isCurrentTaskModal(taskType, generation)) return null;
    try {
        const response = await fetch(url, options);
        if (!isCurrentTaskModal(taskType, generation)) return null;
        if (!response.ok) return null;
        if (response.status === 204) return null;
        const data = await response.json();
        if (!isCurrentTaskModal(taskType, generation)) return null;
        return data;
    } catch (e) {
        return null;
    }
}

async function fetchAndCacheBatchLogs(taskType, executionId, generation) {
    const url = `${contextPath}/api/task-progress/${taskType}/logs?executionId=${executionId}&batchOnly=true`;
    const logs = await fetchJsonForCurrentTaskModal(taskType, generation, url);
    if (logs == null) return false;

    if (currentTaskModal) {
        currentTaskModal.batchLogsByExecutionId[executionId] = logs;
    }
    if (currentTaskModal && currentTaskModal.expandedExecutionId === executionId) {
        updateTaskModalLogs(executionId, logs);
    }
    return true;
}

async function refreshRunListUntilExecutionTerminal(taskType, executionId, generation, maxAttempts, intervalMs) {
    let backoffDelays = [500, 1000, 2000, 3000, 5000];
    if (maxAttempts !== undefined && intervalMs !== undefined) {
        backoffDelays = Array(maxAttempts).fill(intervalMs);
    }
    const attempts = backoffDelays.length;
    for (let attempt = 0; attempt < attempts; attempt++) {
        if (!isCurrentTaskModal(taskType, generation)) return false;
        let isTerminal = false;
        try {
            const url = `${contextPath}/api/task-progress/${taskType}/executions?limit=20`;
            const runs = await fetchJsonForCurrentTaskModal(taskType, generation, url);
            if (runs) {
                const targetRun = runs.find(r => r.executionId === executionId);
                if (targetRun && isExecutionTerminal(targetRun.status)) {
                    renderRunList(runs, taskType, generation);
                    updateExpandedFromCache(taskType, generation);
                    isTerminal = true;
                }
            }
        } catch (e) { /* 静默 */ }

        if (isTerminal) return true;
        if (attempt < attempts - 1) {
            await new Promise(resolve => setTimeout(resolve, backoffDelays[attempt]));
        }
    }
    return false;
}

function observeTaskModalProgress(progress, generation) {
    if (!currentTaskModal || currentTaskModal.generation !== generation) return false;
    currentTaskModal.lastProgressStatus = progress.status;
    if (isProgressTerminal(progress.status) && !currentTaskModal.terminalObserved) {
        currentTaskModal.terminalObserved = true;
        currentTaskModal.terminalProgressSnapshot = progress;
        return true;
    }
    return false;
}

function finalizeCurrentTaskModalTerminal(taskType, generation) {
    if (!isCurrentTaskModal(taskType, generation)) return false;

    const modal = currentTaskModal;
    if (!modal.terminalObserved) return false;
    if (modal.executionId == null) return false;
    if (modal.terminalFinalizationPromise) return modal.terminalFinalizationPromise;

    modal.terminalFinalizationStarted = true;
    modal.terminalFinalizationPromise = runTerminalFinalization(taskType, generation, modal.executionId);
    return modal.terminalFinalizationPromise;
}

async function runTerminalFinalization(taskType, generation, executionId) {
    if (!isCurrentTaskModal(taskType, generation)) return false;
    const modal = currentTaskModal;
    const reachedTerminal = await refreshRunListUntilExecutionTerminal(taskType, executionId, generation);

    if (!isCurrentTaskModal(taskType, generation)) return false;

    const maxLogAttempts = 3;
    const logRetryIntervalMs = 300;
    let logsLoaded = false;
    for (let attempt = 0; attempt < maxLogAttempts; attempt++) {
        if (!isCurrentTaskModal(taskType, generation)) break;
        logsLoaded = await fetchAndCacheBatchLogs(taskType, executionId, generation);
        if (logsLoaded) break;
        if (attempt < maxLogAttempts - 1) {
            await new Promise(resolve => setTimeout(resolve, logRetryIntervalMs));
        }
    }

    if (!isCurrentTaskModal(taskType, generation)) return false;
    modal.terminalLogsSynchronized = logsLoaded;

    if (modal.runListTimer) {
        clearInterval(modal.runListTimer);
        modal.runListTimer = null;
    }

    if (reachedTerminal) {
        modal.terminalExecutionReconciled = true;
        modal.terminalFinalized = true;
    } else {
        modal.terminalFinalizationFailed = true;
        try {
            await fetchRunList(taskType, generation);
        } catch (e) {}
    }

    if (modal.terminalProgressSnapshot && modal.terminalProgressSnapshot.status === "COMPLETED") {
        setTimeout(() => {
            if (isCurrentTaskModal(taskType, generation)) {
                notifyTaskCompletionOnce({
                    taskType,
                    executionId,
                    status: modal.terminalProgressSnapshot.status,
                    message: `${modal.label || "任务"} 完成`,
                    level: "ok",
                    preferDetailed: modal.launchRequested
                });
            }
        }, 500);
    }

    return true;
}

function shouldStartTaskWatcherOnClose(modal) {
    if (!modal) return false;
    if (modal.mode !== "PROGRESS") return false;
    if (modal.terminalObserved) return false;

    const status = modal.lastProgressStatus;
    const expectedActive = (modal.launchRequested || modal.knownActiveAtOpen) && status == null;
    const knownActive = status === "RUNNING" || status === "CANCELLING";

    return expectedActive || knownActive;
}

function notifyTaskCompletionOnce(options) {
    const taskType = options.taskType;
    const executionId = options.executionId;
    const status = options.status;
    const key = executionId != null
        ? `${taskType}:${executionId}`
        : `${taskType}:${status}:active`;
    const activeKey = `${taskType}:${status}:active`;

    if (options.preferDetailed) return false;
    if (executionId != null && taskCompletionNotifications.has(activeKey)) return false;
    if (taskCompletionNotifications.has(key)) return false;
    taskCompletionNotifications.add(key);
    showStatus(options.message, options.level);
    return true;
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        taskModalGenerationSequence,
        nextTaskModalGeneration,
        createTaskModalContext,
        isProgressTerminal,
        isExecutionTerminal,
        isCurrentTaskModal,
        bindTaskModalExecution,
        adoptTaskModalExecution,
        selectExecutionForCurrentModal,
        fetchJsonForCurrentTaskModal,
        fetchAndCacheBatchLogs,
        refreshRunListUntilExecutionTerminal,
        observeTaskModalProgress,
        finalizeCurrentTaskModalTerminal,
        runTerminalFinalization,
        shouldStartTaskWatcherOnClose,
        notifyTaskCompletionOnce,
        getCurrentTaskModal: () => currentTaskModal,
        setCurrentTaskModal: (val) => { currentTaskModal = val; },
        setTaskModalGenerationSequence: (val) => { taskModalGenerationSequence = val; },
        resetTaskCompletionNotifications: () => { taskCompletionNotifications = new Set(); }
    };
}
