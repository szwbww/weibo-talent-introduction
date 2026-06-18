const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

const runtimePath = path.join(__dirname, "..", "..", "main", "resources", "static", "task-modal-runtime.js");
const runtimeSource = fs.readFileSync(runtimePath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createFreshSandbox() {
    const sandbox = {
        contextPath: "/subpath",
        TASK_WATCHER_INTERVAL_MS: 3000,
        TASK_WATCHER_LAUNCH_GRACE_MS: 30000,
        TASK_WATCHER_MAX_INITIAL_204: 10,
        taskWatchers: {},
        taskButtonMapping: {
            EXPERT_REVALIDATION: { btnId: "revalidateBtn", label: "重新验证" },
            RAW_PROMOTION_SCAN: { btnId: "promoteRawBtn", label: "扫描" }
        },

        // Mock DOM functions
        $: (sel) => {
            return {
                textContent: "",
                className: "",
                style: { width: "" },
                hidden: false,
                disabled: false
            };
        },
        $$: (sel) => [],
        document: {
            body: {
                classList: {
                    add: () => {},
                    remove: () => {}
                }
            }
        },

        // Mock DOM renderers
        renderRunList: () => {},
        updateExpandedFromCache: () => {},
        updateTaskModalLogs: () => {},
        showStatus: () => {},
        restoreTaskButton: () => {},
        stopTaskWatcher: () => {},
        startTaskWatcher: () => {},
        stopTaskModalPolling: () => {},
        stopBatchSendStatusPoll: () => {},
        fetchRunList: async () => {},

        // Timer mocks
        setInterval: (fn, ms) => {
            return { fn, ms, id: Math.random() };
        },
        clearInterval: (timer) => {},
        setTimeout: (fn, ms) => {
            fn();
            return { fn, ms, id: Math.random() };
        },
        clearTimeout: (timer) => {},

        // Fetch mock
        fetch: async (url, options) => {
            return {
                ok: true,
                status: 200,
                json: async () => ({})
            };
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(runtimeSource, sandbox);
    vm.runInContext(extractFn("closeTaskModal"), sandbox);
    vm.runInContext(extractFn("startTaskWatcher"), sandbox);
    vm.runInContext(extractFn("stopTaskWatcher"), sandbox);
    vm.runInContext(extractFn("pollTaskWatcher"), sandbox);
    vm.runInContext(extractFn("markTaskWatcherLaunchSucceeded"), sandbox);
    vm.runInContext(extractFn("executeRevalidate"), sandbox);
    vm.runInContext(extractFn("isCurrentTaskWatcher"), sandbox);
    return sandbox;
}

describe("Task Modal State Machine & Runtime Tests", () => {

    describe("generation lifecycle", () => {
        it("closed and reopened modal does not reuse generation", () => {
            const sandbox = createFreshSandbox();
            const ctx1 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx1;
            assert.strictEqual(ctx1.generation, 1);

            sandbox.closeTaskModal();
            assert.strictEqual(sandbox.currentTaskModal, null);

            const ctx2 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            assert.strictEqual(ctx2.generation, 2);
        });

        it("stale response isolation: ignores old response for same taskType", async () => {
            const sandbox = createFreshSandbox();
            const ctx1 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx1;

            // Start a fetch representing generation 1
            const fetchPromise = sandbox.fetchJsonForCurrentTaskModal("EXPERT_REVALIDATION", ctx1.generation, "/api");

            // Close and reopen (now generation is 2)
            sandbox.closeTaskModal();
            const ctx2 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx2;

            // Resolve the fetch from generation 1
            const result = await fetchPromise;
            assert.strictEqual(result, null);
        });

        it("ignores response for different taskType", async () => {
            const sandbox = createFreshSandbox();
            const ctx1 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx1;

            // Start a fetch for EXPERT_REVALIDATION
            const fetchPromise = sandbox.fetchJsonForCurrentTaskModal("EXPERT_REVALIDATION", ctx1.generation, "/api");

            // Switch to RAW_PROMOTION_SCAN
            sandbox.closeTaskModal();
            const ctx2 = sandbox.createTaskModalContext("RAW_PROMOTION_SCAN", "扫描", "btn2", "PROGRESS");
            sandbox.currentTaskModal = ctx2;

            const result = await fetchPromise;
            assert.strictEqual(result, null);
        });
    });

    describe("executionId binding", () => {
        it("bindTaskModalExecution binds executionId from launch response", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            const bound = await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx.generation, 12345);
            assert.strictEqual(bound, true);
            assert.strictEqual(ctx.executionId, 12345);
            assert.strictEqual(ctx.expandedExecutionId, 12345);
        });

        it("bindTaskModalExecution returns false if generation mismatch", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            const bound = await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx.generation + 1, 12345);
            assert.strictEqual(bound, false);
            assert.strictEqual(ctx.executionId, null);
        });
    });

    describe("terminal reconciliation and retry delay", () => {
        it("refreshRunListUntilExecutionTerminal delays on failure (avoids busy loops)", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            let fetchCount = 0;
            sandbox.fetch = async (url) => {
                fetchCount++;
                if (fetchCount < 3) {
                    return { ok: false, status: 500 };
                }
                return {
                    ok: true,
                    status: 200,
                    json: async () => [
                        { executionId: 123, status: "SUCCESS" }
                    ]
                };
            };

            const reached = await sandbox.refreshRunListUntilExecutionTerminal("EXPERT_REVALIDATION", 123, ctx.generation, 5, 10);
            assert.strictEqual(reached, true);
            assert.strictEqual(fetchCount, 3);
        });
    });

    describe("final logs synchronization", () => {
        it("fetchAndCacheBatchLogs caches logs and updates DOM if expanded", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.expandedExecutionId = 123;

            let updatedExecId = null;
            let updatedLogs = null;
            sandbox.updateTaskModalLogs = (id, logs) => {
                updatedExecId = id;
                updatedLogs = logs;
            };

            sandbox.fetch = async () => {
                return {
                    ok: true,
                    status: 200,
                    json: async () => [{ batchNumber: 1, batchProcessed: 10 }]
                };
            };

            const success = await sandbox.fetchAndCacheBatchLogs("EXPERT_REVALIDATION", 123, ctx.generation);
            assert.strictEqual(success, true);
            assert.strictEqual(updatedExecId, 123);
            assert.deepStrictEqual(updatedLogs, [{ batchNumber: 1, batchProcessed: 10 }]);
            assert.deepStrictEqual(ctx.batchLogsByExecutionId[123], [{ batchNumber: 1, batchProcessed: 10 }]);
        });
    });

    describe("watcher lifecycle", () => {
        it("watcher starts when PROGRESS modal is closed with running status", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.lastProgressStatus = "RUNNING";

            let startedWatcherType = null;
            sandbox.startTaskWatcher = (taskType) => {
                startedWatcherType = taskType;
            };

            sandbox.closeTaskModal();
            assert.strictEqual(startedWatcherType, "EXPERT_REVALIDATION");
        });

        it("watcher does NOT start when CONFIG modal is closed", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "CONFIG");
            sandbox.currentTaskModal = ctx;

            let startedWatcherType = null;
            sandbox.startTaskWatcher = (taskType) => {
                startedWatcherType = taskType;
            };

            sandbox.closeTaskModal();
            assert.strictEqual(startedWatcherType, null);
        });

        it("watcher starts when known active modal is closed before first progress", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.knownActiveAtOpen = true;
            ctx.launchRequested = false;
            ctx.lastProgressStatus = null;

            let startedWatcherType = null;
            sandbox.startTaskWatcher = (taskType) => {
                startedWatcherType = taskType;
            };

            sandbox.closeTaskModal();
            assert.strictEqual(startedWatcherType, "EXPERT_REVALIDATION");
        });

        it("watcher does not start for unknown non-launch modal with no progress", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.knownActiveAtOpen = false;
            ctx.launchRequested = false;
            ctx.lastProgressStatus = null;

            let watcherStarted = false;
            sandbox.startTaskWatcher = () => { watcherStarted = true; };

            sandbox.closeTaskModal();
            assert.strictEqual(watcherStarted, false);
        });
    });

    describe("R5 run-list binding and notifications", () => {
        it("selectExecutionForCurrentModal binds newest running execution for known active modal", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            ctx.knownActiveAtOpen = true;
            const selected = sandbox.selectExecutionForCurrentModal([
                { executionId: 101, status: "RUNNING", startedAt: "2026-06-11T10:00:00" },
                { executionId: 102, status: "RUNNING", startedAt: "2026-06-11T10:01:00" }
            ], ctx);

            assert.strictEqual(selected.executionId, 102);
        });

        it("selectExecutionForCurrentModal binds newest terminal only after known active modal observes terminal", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            ctx.knownActiveAtOpen = true;
            ctx.terminalObserved = true;
            const selected = sandbox.selectExecutionForCurrentModal([
                { executionId: 201, status: "SUCCESS", startedAt: "2026-06-11T10:00:00" },
                { executionId: 202, status: "SUCCESS", startedAt: "2026-06-11T10:01:00" }
            ], ctx);

            assert.strictEqual(selected.executionId, 202);
        });

        it("selectExecutionForCurrentModal does not guess for CONFIG or unknown progress modals", () => {
            const sandbox = createFreshSandbox();
            const configCtx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "CONFIG");
            const progressCtx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            const runs = [{ executionId: 301, status: "SUCCESS", startedAt: "2026-06-11T10:00:00" }];

            assert.strictEqual(sandbox.selectExecutionForCurrentModal(runs, configCtx), null);
            assert.strictEqual(sandbox.selectExecutionForCurrentModal(runs, progressCtx), null);
        });

        it("fetchRunList adopts execution from run list and finalizes known-active fast terminal", async () => {
            const sandbox = createFreshSandbox();
            vm.runInContext(extractFn("fetchRunList"), sandbox);
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.knownActiveAtOpen = true;
            ctx.terminalObserved = true;
            ctx.terminalProgressSnapshot = { status: "COMPLETED" };

            let logsFetchedFor = null;
            sandbox.fetchJsonForCurrentTaskModal = async (taskType, gen, url) => {
                if (url.includes("/executions")) {
                    return [{ executionId: 401, status: "SUCCESS", startedAt: "2026-06-11T10:01:00" }];
                }
                if (url.includes("/logs")) {
                    logsFetchedFor = url;
                    return [];
                }
                return null;
            };

            await sandbox.fetchRunList("EXPERT_REVALIDATION", ctx.generation);

            assert.strictEqual(ctx.executionId, 401);
            assert.strictEqual(ctx.expandedExecutionId, 401);
            assert.strictEqual(ctx.terminalFinalized, true);
            assert.ok(logsFetchedFor.includes("executionId=401"));
        });

        it("finalization returns the same promise for concurrent callers", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.terminalObserved = true;
            ctx.executionId = 501;

            let releaseRefresh;
            sandbox.refreshRunListUntilExecutionTerminal = () => new Promise(resolve => {
                releaseRefresh = () => resolve(true);
            });
            sandbox.fetchAndCacheBatchLogs = async () => true;

            const first = sandbox.finalizeCurrentTaskModalTerminal("EXPERT_REVALIDATION", ctx.generation);
            const second = sandbox.finalizeCurrentTaskModalTerminal("EXPERT_REVALIDATION", ctx.generation);

            assert.strictEqual(first, second);
            assert.strictEqual(ctx.terminalFinalized, false);
            releaseRefresh();
            await second;
            assert.strictEqual(ctx.terminalFinalized, true);
        });

        it("notifyTaskCompletionOnce deduplicates generic and detailed notifications", () => {
            const sandbox = createFreshSandbox();
            const messages = [];
            sandbox.showStatus = (message, level) => messages.push({ message, level });

            assert.strictEqual(sandbox.notifyTaskCompletionOnce({
                taskType: "EXPERT_REVALIDATION",
                status: "COMPLETED",
                message: "重新验证 已完成",
                level: "ok"
            }), true);
            assert.strictEqual(sandbox.notifyTaskCompletionOnce({
                taskType: "EXPERT_REVALIDATION",
                executionId: 601,
                status: "COMPLETED",
                message: "候选人重新验证完成",
                level: "ok"
            }), false);

            assert.deepStrictEqual(messages, [{ message: "重新验证 已完成", level: "ok" }]);
        });

        it("handle task entrypoints pass knownActiveAtOpen when isTaskRunning is true", async () => {
            const sandbox = createFreshSandbox();
            vm.runInContext(extractFn("handleRevalidateCandidates"), sandbox);
            vm.runInContext(extractFn("handlePromoteRaw"), sandbox);
            vm.runInContext(extractFn("handleDiscover"), sandbox);

            sandbox.isTaskRunning = async () => true;
            const calls = [];
            sandbox.openTaskModal = (...args) => calls.push(args);

            await sandbox.handleRevalidateCandidates();
            await sandbox.handlePromoteRaw();
            await sandbox.handleDiscover();

            assert.deepStrictEqual(calls.map(call => call[3].knownActiveAtOpen), [true, true, true]);
        });
    });

    describe("R4 verification test cases", () => {
        it("Case 1: progress terminal arrives first, executionId binds later", async () => {
            const sandbox = createFreshSandbox();
            vm.runInContext(extractFn("updateTaskModalFromProgress"), sandbox);

            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            let fetchRunListCalled = false;
            let fetchLogsCalled = false;
            sandbox.fetchRunList = async () => { fetchRunListCalled = true; };
            sandbox.fetchJsonForCurrentTaskModal = async (taskType, gen, url) => {
                if (url.includes("/executions")) {
                    fetchRunListCalled = true;
                    return [{ executionId: 123, status: "SUCCESS" }];
                }
                if (url.includes("/logs")) {
                    fetchLogsCalled = true;
                    return [{ batchNumber: 1, batchProcessed: 10 }];
                }
                return null;
            };

            const progress = { status: "COMPLETED", percentage: 100, message: "完成" };
            sandbox.updateTaskModalFromProgress(progress, ctx.generation);

            assert.strictEqual(ctx.terminalObserved, true);
            assert.strictEqual(ctx.terminalFinalized, false);
            assert.strictEqual(fetchLogsCalled, false);

            const bound = await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx.generation, 123);
            assert.strictEqual(bound, true);
            assert.strictEqual(ctx.executionId, 123);

            await new Promise(resolve => setTimeout(resolve, 50));

            assert.strictEqual(ctx.terminalFinalized, true);
            assert.strictEqual(fetchLogsCalled, true);
        });

        it("Case 2: executionId binds first, progress terminal arrives later", async () => {
            const sandbox = createFreshSandbox();
            vm.runInContext(extractFn("updateTaskModalFromProgress"), sandbox);

            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            let fetchLogsCount = 0;
            sandbox.fetchJsonForCurrentTaskModal = async (taskType, gen, url) => {
                if (url.includes("/executions")) {
                    return [{ executionId: 123, status: "SUCCESS" }];
                }
                if (url.includes("/logs")) {
                    fetchLogsCount++;
                    return [{ batchNumber: 1, batchProcessed: 10 }];
                }
                return null;
            };

            const bound = await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx.generation, 123);
            assert.strictEqual(bound, true);
            assert.strictEqual(ctx.executionId, 123);
            assert.strictEqual(ctx.terminalFinalized, false);

            const progress = { status: "COMPLETED", percentage: 100, message: "完成" };
            sandbox.updateTaskModalFromProgress(progress, ctx.generation);

            await new Promise(resolve => setTimeout(resolve, 50));
            assert.strictEqual(ctx.terminalFinalized, true);
            assert.strictEqual(fetchLogsCount, 1);
        });

        it("Case 3: duplicate terminal progress does not re-trigger finalization", async () => {
            const sandbox = createFreshSandbox();
            vm.runInContext(extractFn("updateTaskModalFromProgress"), sandbox);

            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            let fetchLogsCount = 0;
            sandbox.fetchJsonForCurrentTaskModal = async (taskType, gen, url) => {
                if (url.includes("/executions")) {
                    return [{ executionId: 123, status: "SUCCESS" }];
                }
                if (url.includes("/logs")) {
                    fetchLogsCount++;
                    return [{ batchNumber: 1, batchProcessed: 10 }];
                }
                return null;
            };

            await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx.generation, 123);

            const progress = { status: "COMPLETED", percentage: 100, message: "完成" };
            sandbox.updateTaskModalFromProgress(progress, ctx.generation);
            await new Promise(resolve => setTimeout(resolve, 50));
            assert.strictEqual(ctx.terminalFinalized, true);
            assert.strictEqual(fetchLogsCount, 1);

            sandbox.updateTaskModalFromProgress(progress, ctx.generation);
            await new Promise(resolve => setTimeout(resolve, 50));
            assert.strictEqual(fetchLogsCount, 1);
        });

        it("Case 4: POST response binds after modal is closed, should do nothing", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            sandbox.closeTaskModal();

            const bound = await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx.generation, 123);
            assert.strictEqual(bound, false);
            assert.strictEqual(ctx.executionId, null);
        });

        it("Case 5: POST response binds after new generation has opened", async () => {
            const sandbox = createFreshSandbox();
            const ctx1 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx1;

            sandbox.closeTaskModal();
            const ctx2 = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx2;

            const bound = await sandbox.bindTaskModalExecution("EXPERT_REVALIDATION", ctx1.generation, 123);
            assert.strictEqual(bound, false);
            assert.strictEqual(ctx1.executionId, null);
            assert.strictEqual(ctx2.executionId, null);
        });

        it("Case 6: closing terminal modal does NOT start watcher", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.launchRequested = true;
            ctx.terminalObserved = true;
            ctx.lastProgressStatus = "COMPLETED";

            let watcherStarted = false;
            sandbox.startTaskWatcher = () => { watcherStarted = true; };

            sandbox.closeTaskModal();
            assert.strictEqual(watcherStarted, false);
        });

        it("Case 7: closing modal while launch in flight (no progress yet) starts watcher", () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.launchRequested = true;
            ctx.lastProgressStatus = null;
            ctx.terminalObserved = false;

            let watcherStarted = false;
            sandbox.startTaskWatcher = () => { watcherStarted = true; };

            sandbox.closeTaskModal();
            assert.strictEqual(watcherStarted, true);
        });

        it("Case 8: runListTimer is cleared even when executions fails continuously", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.runListTimer = { id: 123 };

            sandbox.fetchJsonForCurrentTaskModal = async () => null;

            ctx.terminalObserved = true;
            ctx.executionId = 123;

            let refreshCalled = false;
            sandbox.refreshRunListUntilExecutionTerminal = async () => {
                refreshCalled = true;
                return false;
            };

            let clearedTimerId = null;
            sandbox.clearInterval = (timer) => { clearedTimerId = timer.id; };

            await sandbox.finalizeCurrentTaskModalTerminal("EXPERT_REVALIDATION", ctx.generation);

            assert.strictEqual(refreshCalled, true);
            assert.strictEqual(clearedTimerId, 123);
            assert.strictEqual(ctx.terminalFinalizationFailed, true);
        });

        it("Case 9: final logs fetch succeeds on 3rd attempt", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.runListTimer = { id: 123 };

            sandbox.refreshRunListUntilExecutionTerminal = async () => true;

            let logAttempts = 0;
            sandbox.fetchJsonForCurrentTaskModal = async (taskType, gen, url) => {
                if (url.includes("/logs")) {
                    logAttempts++;
                    if (logAttempts < 3) return null;
                    return [{ batchNumber: 1, batchProcessed: 10 }];
                }
                return null;
            };

            ctx.terminalObserved = true;
            ctx.executionId = 123;

            await sandbox.finalizeCurrentTaskModalTerminal("EXPERT_REVALIDATION", ctx.generation);

            assert.strictEqual(logAttempts, 3);
            assert.strictEqual(ctx.terminalFinalized, true);
            assert.deepStrictEqual(ctx.batchLogsByExecutionId[123], [{ batchNumber: 1, batchProcessed: 10 }]);
        });

        it("Case 10: final logs fetch fails all 3 attempts but terminates successfully", async () => {
            const sandbox = createFreshSandbox();
            const ctx = sandbox.createTaskModalContext("EXPERT_REVALIDATION", "重新验证", "btn1", "PROGRESS");
            sandbox.currentTaskModal = ctx;
            ctx.runListTimer = { id: 123 };

            sandbox.refreshRunListUntilExecutionTerminal = async () => true;

            let logAttempts = 0;
            sandbox.fetchJsonForCurrentTaskModal = async (taskType, gen, url) => {
                if (url.includes("/logs")) {
                    logAttempts++;
                    return null;
                }
                return null;
            };

            ctx.terminalObserved = true;
            ctx.executionId = 123;

            let clearedTimerId = null;
            sandbox.clearInterval = (timer) => { clearedTimerId = timer.id; };

            await sandbox.finalizeCurrentTaskModalTerminal("EXPERT_REVALIDATION", ctx.generation);

            assert.strictEqual(logAttempts, 3);
            assert.strictEqual(ctx.terminalFinalized, true);
            assert.strictEqual(clearedTimerId, 123);
        });
    });

    describe("204 launch race condition tests", () => {
        it("Case 1: 启动阶段首次 204 不停止 watcher", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            // Mock fetch to return 204
            sandbox.fetch = async () => ({ status: 204 });

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });

            // Wait for any async microtasks
            await new Promise(resolve => setImmediate(resolve));

            const watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.awaitingLaunch, true);
            assert.strictEqual(watcher.noProgressCount, 1);
            assert.strictEqual(restored, false);
        });

        it("Case 2: 多个 204 后观察到 RUNNING", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount <= 2) {
                    return { status: 204 };
                }
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: "RUNNING" })
                };
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });

            // First fetch (triggered immediately by startTaskWatcher) returns 204
            await new Promise(resolve => setImmediate(resolve));
            let watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.noProgressCount, 1);
            assert.strictEqual(watcher.awaitingLaunch, true);

            // Second fetch
            await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");
            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.noProgressCount, 2);
            assert.strictEqual(watcher.awaitingLaunch, true);

            // Third fetch: returns RUNNING
            await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");
            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.noProgressCount, 0);
            assert.strictEqual(watcher.awaitingLaunch, false);
            assert.strictEqual(watcher.observedActive, true);
            assert.strictEqual(restored, false);
        });

        it("Case 3: RUNNING 后再收到 204", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount === 1) {
                    return {
                        ok: true,
                        status: 200,
                        json: async () => ({ status: "RUNNING" })
                    };
                }
                return { status: 204 };
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            await new Promise(resolve => setImmediate(resolve));

            let watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.observedActive, true);

            // Next poll returns 204
            await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");
            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, true);
        });

        it("Case 4: 启动宽限期耗尽 (次数耗尽)", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            sandbox.fetch = async () => ({ status: 204 });

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            await new Promise(resolve => setImmediate(resolve));

            let watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.noProgressCount, 1);

            // Poll 9 more times to reach MAX_INITIAL_204 (10)
            for (let i = 0; i < 9; i++) {
                await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");
            }

            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, true);
        });

        it("Case 4b: 启动宽限期耗尽 (时间耗尽)", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            sandbox.fetch = async () => ({ status: 204 });

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            await new Promise(resolve => setImmediate(resolve));

            let watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);

            // Artificially age the watcher's startedAt
            watcher.startedAt = Date.now() - 31000;

            await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");

            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, true);
        });

        it("Case 5: 启动阶段直接观察到终态", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };
            let notifications = [];
            sandbox.showStatus = (msg, level) => { notifications.push({ msg, level }); };

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount === 1) return { status: 204 };
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({
                        status: "COMPLETED",
                        executionId: 456
                    })
                };
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            await new Promise(resolve => setImmediate(resolve));

            let watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);

            await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");

            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, true);
            assert.strictEqual(notifications.length, 1);
            assert.ok(notifications[0].msg.includes("已完成"));
        });

        it("Case 6: 非 awaitingLaunch watcher 收到 204", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            sandbox.fetch = async () => ({ status: 204 });

            sandbox.startTaskWatcher("EXPERT_REVALIDATION"); // no awaitingLaunch
            await new Promise(resolve => setImmediate(resolve));

            const watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, true);
        });

        it("Case 7: 重复 start 不创建多个 interval", async () => {
            const sandbox = createFreshSandbox();
            let intervals = [];
            sandbox.setInterval = (fn, ms) => {
                const id = Math.random();
                intervals.push({ id, fn, ms });
                return id;
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION");
            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });

            assert.strictEqual(intervals.length, 1);
            const watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher.awaitingLaunch, true);
        });

        it("Case 8: fetch 期间 watcher 被删除", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            // Mock fetch to be slow or wait
            let resolveFetch;
            sandbox.fetch = () => new Promise(resolve => { resolveFetch = resolve; });

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });

            // While fetch is pending, stop the watcher
            sandbox.stopTaskWatcher("EXPERT_REVALIDATION", false);

            // Now resolve the fetch with RUNNING status
            resolveFetch({
                ok: true,
                status: 200,
                json: async () => ({ status: "RUNNING" })
            });

            await new Promise(resolve => setImmediate(resolve));

            const watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, false);
        });

        it("Case 9: POST 成功时弹窗已关闭", async () => {
            const sandbox = createFreshSandbox();

            // Mock modal not matching (i.e. closed)
            sandbox.isCurrentTaskModal = () => false;

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });

            sandbox.markTaskWatcherLaunchSucceeded("EXPERT_REVALIDATION", 1);

            const watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);
            assert.strictEqual(watcher.awaitingLaunch, false);
            assert.strictEqual(watcher.observedActive, true);
            assert.strictEqual(watcher.noProgressCount, 0);
        });

        it("Case 10: POST 失败清理 awaitingLaunch watcher", async () => {
            const sandbox = createFreshSandbox();
            let restored = false;
            sandbox.restoreTaskButton = () => { restored = true; };

            sandbox.progressStoreHasRunningTask = async () => false;
            sandbox.openTaskModal = (taskType, label, btnId, options) => {
                sandbox.currentTaskModal = sandbox.createTaskModalContext(taskType, label, btnId, "PROGRESS");
                sandbox.currentTaskModal.launchRequested = options.launchRequested;
            };
            sandbox.api = async () => {
                throw new Error("正在执行中");
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });

            let watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcher);

            try {
                await sandbox.executeRevalidate();
            } catch (e) {
                // Ignore
            }

            watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.strictEqual(watcher, undefined);
            assert.strictEqual(restored, true);
        });
    });

    describe("204 launch race condition identity tests", () => {
        it("Case 11: 旧 204 不得停止新 watcher", async () => {
            const sandbox = createFreshSandbox();
            let restoredCount = 0;
            sandbox.restoreTaskButton = () => { restoredCount++; };

            // A's fetch resolver
            let resolveA;
            const promiseA = new Promise(resolve => { resolveA = resolve; });

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount === 1) {
                    await promiseA;
                    return { status: 204 };
                }
                // B's fetch is pending
                return new Promise(() => {});
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherA = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcherA);

            // Stop A
            sandbox.stopTaskWatcher("EXPERT_REVALIDATION", false);

            // Create B
            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherB = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.ok(watcherB);
            assert.notStrictEqual(watcherA, watcherB);

            // Let A resolve
            resolveA();
            await new Promise(resolve => setImmediate(resolve));

            // Verify B is untouched
            assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], watcherB);
            assert.strictEqual(watcherB.noProgressCount, 0);
            assert.strictEqual(restoredCount, 0);
        });

        it("Case 12: 旧 RUNNING 不得修改新 watcher", async () => {
            const sandbox = createFreshSandbox();

            let resolveA;
            const promiseA = new Promise(resolve => { resolveA = resolve; });

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount === 1) {
                    await promiseA;
                    return {
                        ok: true,
                        status: 200,
                        json: async () => ({ status: "RUNNING" })
                    };
                }
                return new Promise(() => {});
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherA = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            // Stop A and create B
            sandbox.stopTaskWatcher("EXPERT_REVALIDATION", false);
            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherB = sandbox.taskWatchers["EXPERT_REVALIDATION"];
            assert.notStrictEqual(watcherA, watcherB);

            // Let A resolve
            resolveA();
            await new Promise(resolve => setImmediate(resolve));

            // Verify B's state is not contaminated by A's RUNNING
            assert.strictEqual(watcherB.awaitingLaunch, true);
            assert.strictEqual(watcherB.observedActive, false);
        });

        it("Case 13: 旧 COMPLETED 不得停止或通知新 watcher", async () => {
            const sandbox = createFreshSandbox();
            let restoredCount = 0;
            sandbox.restoreTaskButton = () => { restoredCount++; };
            let notifications = [];
            sandbox.showStatus = (msg, level) => { notifications.push({ msg, level }); };

            let resolveA;
            const promiseA = new Promise(resolve => { resolveA = resolve; });

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount === 1) {
                    await promiseA;
                    return {
                        ok: true,
                        status: 200,
                        json: async () => ({ status: "COMPLETED", executionId: 777 })
                    };
                }
                return new Promise(() => {});
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherA = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            sandbox.stopTaskWatcher("EXPERT_REVALIDATION", false);
            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherB = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            resolveA();
            await new Promise(resolve => setImmediate(resolve));

            assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], watcherB);
            assert.strictEqual(restoredCount, 0);
            assert.strictEqual(notifications.length, 0);
        });

        it("Case 14: JSON 解析期间替换 watcher", async () => {
            const sandbox = createFreshSandbox();

            let resolveJson;
            const jsonPromise = new Promise(resolve => { resolveJson = resolve; });

            let fetchCount = 0;
            sandbox.fetch = async () => {
                fetchCount++;
                if (fetchCount === 1) {
                    return {
                        ok: true,
                        status: 200,
                        json: () => jsonPromise
                    };
                }
                return new Promise(() => {});
            };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherA = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            // A's fetch completed, now json() is pending.
            await new Promise(resolve => setImmediate(resolve));

            // Stop A and create B
            sandbox.stopTaskWatcher("EXPERT_REVALIDATION", false);
            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherB = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            // Resolve A's JSON to CANCELLED
            resolveJson({ status: "CANCELLED", executionId: 888 });
            await new Promise(resolve => setImmediate(resolve));

            // B is unaffected
            assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], watcherB);
        });

        it("Case 15: expected watcher 防止误删", async () => {
            const sandbox = createFreshSandbox();
            let restoredCount = 0;
            sandbox.restoreTaskButton = () => { restoredCount++; };

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherA = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            // Stop A
            sandbox.stopTaskWatcher("EXPERT_REVALIDATION", false);

            // Create B
            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcherB = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            // Call stopTaskWatcher with A as expected instance
            const stopped = sandbox.stopTaskWatcher("EXPERT_REVALIDATION", true, watcherA);
            assert.strictEqual(stopped, false);
            assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], watcherB);
            assert.strictEqual(restoredCount, 0);
        });

        it("Case 16: 当前实例终态仍正常停止", async () => {
            const sandbox = createFreshSandbox();
            let restoredCount = 0;
            sandbox.restoreTaskButton = () => { restoredCount++; };
            let notifications = [];
            sandbox.showStatus = (msg, level) => { notifications.push({ msg, level }); };

            sandbox.fetch = async () => ({
                ok: true,
                status: 200,
                json: async () => ({ status: "COMPLETED", executionId: 999 })
            });

            sandbox.startTaskWatcher("EXPERT_REVALIDATION", { awaitingLaunch: true });
            const watcher = sandbox.taskWatchers["EXPERT_REVALIDATION"];

            await new Promise(resolve => setImmediate(resolve));

            assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], undefined);
            assert.strictEqual(restoredCount, 1);
            assert.strictEqual(notifications.length, 1);
            assert.ok(notifications[0].msg.includes("已完成"));
        });
    });

    describe("Second reverification fix plan tests", () => {
        it("handleBackfillOperatorStatus displays ok status when failure = 0", async () => {
            const sandbox = createFreshSandbox();
            let statusCalled = null;
            sandbox.showStatus = (msg, level) => {
                statusCalled = { msg, level };
            };
            sandbox.api = async (url, options) => {
                return { total: 10, success: 9, failure: 0, skipped: 1 };
            };
            vm.runInContext(extractFn("handleBackfillOperatorStatus"), sandbox);
            await sandbox.handleBackfillOperatorStatus();

            assert.ok(statusCalled);
            assert.strictEqual(statusCalled.level, "ok");
            assert.ok(statusCalled.msg.includes("成功 9"));
            assert.ok(statusCalled.msg.includes("跳过 1"));
        });

        it("handleBackfillOperatorStatus displays warn status when success > 0 && failure > 0", async () => {
            const sandbox = createFreshSandbox();
            let statusCalled = null;
            sandbox.showStatus = (msg, level) => {
                statusCalled = { msg, level };
            };
            sandbox.api = async (url, options) => {
                return { total: 10, success: 8, failure: 2, skipped: 0 };
            };
            vm.runInContext(extractFn("handleBackfillOperatorStatus"), sandbox);
            await sandbox.handleBackfillOperatorStatus();

            assert.ok(statusCalled);
            assert.strictEqual(statusCalled.level, "warn");
            assert.ok(statusCalled.msg.includes("成功 8"));
            assert.ok(statusCalled.msg.includes("失败 2"));
        });

        it("handleBackfillOperatorStatus displays error status when success = 0 && failure > 0", async () => {
            const sandbox = createFreshSandbox();
            let statusCalled = null;
            sandbox.showStatus = (msg, level) => {
                statusCalled = { msg, level };
            };
            sandbox.api = async (url, options) => {
                return { total: 10, success: 0, failure: 10, skipped: 0 };
            };
            vm.runInContext(extractFn("handleBackfillOperatorStatus"), sandbox);
            await sandbox.handleBackfillOperatorStatus();

            assert.ok(statusCalled);
            assert.strictEqual(statusCalled.level, "error");
            assert.ok(statusCalled.msg.includes("失败 10"));
        });

        it("handleBulkOutreach passes knownActiveAtOpen = true if task is running", async () => {
            const sandbox = createFreshSandbox();
            let openedModal = null;
            sandbox.isTaskRunning = async (type) => true;
            sandbox.openTaskModal = (taskType, label, btnId, options) => {
                openedModal = { taskType, label, btnId, options };
            };
            vm.runInContext(extractFn("handleBulkOutreach"), sandbox);
            await sandbox.handleBulkOutreach();

            assert.ok(openedModal);
            assert.strictEqual(openedModal.taskType, "MANUAL_INITIAL_OUTREACH");
            assert.ok(openedModal.options);
            assert.strictEqual(openedModal.options.knownActiveAtOpen, true);
        });

        it("handleCheckReplies passes knownActiveAtOpen = true if task is running", async () => {
            const sandbox = createFreshSandbox();
            let openedModal = null;
            sandbox.isTaskRunning = async (type) => true;
            sandbox.openTaskModal = (taskType, label, btnId, options) => {
                openedModal = { taskType, label, btnId, options };
            };
            vm.runInContext(extractFn("handleCheckReplies"), sandbox);
            await sandbox.handleCheckReplies();

            assert.ok(openedModal);
            assert.strictEqual(openedModal.taskType, "CHECK_REPLIES");
            assert.ok(openedModal.options);
            assert.strictEqual(openedModal.options.knownActiveAtOpen, true);
        });

        it("executeManualOutreach starts watcher and does not notify completion upon acceptance", async () => {
            const sandbox = createFreshSandbox();
            let openedModal = null;
            sandbox.progressStoreHasRunningTask = async () => false;
            sandbox.openTaskModal = (taskType, label, btnId, options) => {
                openedModal = { taskType, label, btnId, options };
                sandbox.currentTaskModal = sandbox.createTaskModalContext(taskType, label, btnId, "PROGRESS");
                sandbox.currentTaskModal.generation = 123;
            };
            let isCurrentCallCount = 0;
            sandbox.isCurrentTaskModal = (taskType, gen) => {
                isCurrentCallCount++;
                return isCurrentCallCount === 1;
            };
            sandbox.api = async (url, options) => {
                return { executionId: 456 };
            };
            let watcherStarted = null;
            sandbox.startTaskWatcher = (taskType, options) => {
                watcherStarted = { taskType, options };
                sandbox.taskWatchers[taskType] = { awaitingLaunch: true };
            };
            let notified = false;
            sandbox.notifyTaskCompletionOnce = () => { notified = true; };
            sandbox.hideProgressBar = () => {};
            sandbox.showTaskErrorLog = () => {};

            vm.runInContext("const BATCH_SEND_TASK_TYPE = \"MANUAL_INITIAL_OUTREACH\";", sandbox);
            vm.runInContext(extractFn("launchBatchSendWithProgress"), sandbox);
            vm.runInContext(extractFn("executeManualOutreach"), sandbox);
            await sandbox.executeManualOutreach();

            assert.ok(openedModal);
            assert.strictEqual(openedModal.options.launchRequested, true);
            assert.strictEqual(sandbox.currentTaskModal.executionId, 456);
            assert.ok(watcherStarted);
            assert.strictEqual(watcherStarted.taskType, "MANUAL_INITIAL_OUTREACH");
            assert.strictEqual(notified, false);
        });

        it("executeCheckReplies starts watcher and does not notify completion upon acceptance", async () => {
            const sandbox = createFreshSandbox();
            let openedModal = null;
            sandbox.progressStoreHasRunningTask = async () => false;
            sandbox.openTaskModal = (taskType, label, btnId, options) => {
                openedModal = { taskType, label, btnId, options };
                sandbox.currentTaskModal = sandbox.createTaskModalContext(taskType, label, btnId, "PROGRESS");
                sandbox.currentTaskModal.generation = 123;
            };
            let isCurrentCallCount = 0;
            sandbox.isCurrentTaskModal = (taskType, gen) => {
                isCurrentCallCount++;
                return isCurrentCallCount === 1;
            };
            sandbox.api = async (url, options) => {
                return { executionId: 789 };
            };
            let watcherStarted = null;
            sandbox.startTaskWatcher = (taskType, options) => {
                watcherStarted = { taskType, options };
                sandbox.taskWatchers[taskType] = { awaitingLaunch: true };
            };
            let notified = false;
            sandbox.notifyTaskCompletionOnce = () => { notified = true; };

            vm.runInContext(extractFn("executeCheckReplies"), sandbox);
            await sandbox.executeCheckReplies();

            assert.ok(openedModal);
            assert.strictEqual(openedModal.options.launchRequested, true);
            assert.strictEqual(sandbox.currentTaskModal.executionId, 789);
            assert.ok(watcherStarted);
            assert.strictEqual(watcherStarted.taskType, "CHECK_REPLIES");
            assert.strictEqual(notified, false);
        });
    });

    describe("Third reverification fix plan tests", () => {
        it("getProgressStatusMeta returns correct label and level", () => {
            const sandbox = createFreshSandbox();
            const getProgressStatusMeta = sandbox.getProgressStatusMeta;

            const completed = getProgressStatusMeta("COMPLETED");
            assert.strictEqual(completed.label, "已完成");
            assert.strictEqual(completed.level, "ok");

            const partial = getProgressStatusMeta("PARTIAL_SUCCESS");
            assert.strictEqual(partial.label, "部分成功");
            assert.strictEqual(partial.level, "warn");

            const failed = getProgressStatusMeta("FAILED");
            assert.strictEqual(failed.label, "失败");
            assert.strictEqual(failed.level, "error");

            const cancelled = getProgressStatusMeta("CANCELLED");
            assert.strictEqual(cancelled.label, "已取消");
            assert.strictEqual(cancelled.level, "warn");

            const unknown = getProgressStatusMeta("UNKNOWN_STATE");
            assert.strictEqual(unknown.label, "失败");
            assert.strictEqual(unknown.level, "error");
        });

        it("updateTaskModalFromProgress sets warning style and label for PARTIAL_SUCCESS", async () => {
            const sandbox = createFreshSandbox();
            const elements = {};
            sandbox.$ = (sel) => {
                if (!elements[sel]) {
                    elements[sel] = {
                        textContent: "",
                        className: "",
                        style: { width: "" },
                        hidden: false,
                        disabled: false
                    };
                }
                return elements[sel];
            };
            vm.runInContext(extractFn("updateTaskModalFromProgress"), sandbox);

            const ctx = sandbox.createTaskModalContext("CHECK_REPLIES", "检查回复", "checkRepliesBtn", "PROGRESS");
            sandbox.currentTaskModal = ctx;

            const progress = { status: "PARTIAL_SUCCESS", percentage: 50, message: "部分邮箱账号检查失败" };
            sandbox.updateTaskModalFromProgress(progress, ctx.generation);

            // Verify cancel button text
            const cancelBtn = elements["#taskModalCancelBtn"];
            assert.ok(cancelBtn);
            assert.strictEqual(cancelBtn.textContent, "部分成功");

            // Verify progress bar class (sync old progress bar)
            const progressBar = elements["#taskProgressBar"];
            assert.ok(progressBar);
            assert.strictEqual(progressBar.className, "task-progress-bar warning");
        });
    });
});
