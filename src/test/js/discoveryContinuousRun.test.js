const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsSource = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");
const html = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");

// vm 沙箱单测以函数为单位抽取源码（与既有 taskModalStateMachine.test.js 同惯例）。
function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

// c3 的连续模式段（常量 + 渲染/轮询接缝）是一整块连续源码，整段载入可保证测的就是生产常量。
function c3Section() {
    const start = appJsSource.indexOf("// c3（I-1/I-2/I-3/I-5/I-7）：深度发现连续模式的模式、来源与状态轮询");
    const end = appJsSource.indexOf("function isCurrentTaskWatcher");
    assert.ok(start > 0 && end > start, "the c3 continuous-mode section must stay in app.js");
    return appJsSource.slice(start, end);
}

function makeElement() {
    return {
        textContent: "",
        innerHTML: "",
        className: "",
        hidden: false,
        disabled: false,
        checked: false,
        value: "",
        style: {},
        parentElement: null
    };
}

function createSandbox(overrides = {}) {
    const elements = {};
    const setIntervalCalls = [];
    const clearedIntervals = [];
    const apiCalls = [];
    const boundExecutions = [];
    const notifications = [];
    const statuses = [];
    const sandbox = {
        console,
        contextPath: "/subpath",
        taskButtonMapping: {
            EXPERT_REVALIDATION: { label: "重新验证", btnId: "discoverBtn" },
            RAW_PROMOTION_SCAN: { label: "扫描", btnId: "discoverBtn" },
            EXPERT_DISCOVERY: { label: "深度发现", btnId: "discoverBtn" },
            EXPERT_ENRICHMENT: { label: "补充数据", btnId: "discoverBtn" },
            MANUAL_INITIAL_OUTREACH: { label: "批量发送", btnId: "bulkOutreachBtn" },
            CHECK_REPLIES: { label: "检查回复", btnId: "checkRepliesBtn" }
        },
        currentTaskModal: null,
        confirm: () => true,
        showStatus: (message, level) => statuses.push({ message, level }),
        escapeHtml: (value) => String(value ?? ""),
        handleAuthResponse: async () => {},
        notifyTaskCompletionOnce: (options) => notifications.push(options),
        bindTaskModalExecution: async (taskType, generation, executionId) => {
            boundExecutions.push({ taskType, generation, executionId });
        },
        isCurrentTaskModal: (taskType, generation) => sandbox.currentTaskModal != null
            && sandbox.currentTaskModal.generation === generation
            && sandbox.currentTaskModal.taskType === taskType,
        stopTaskModalPolling: () => {},
        stopTaskWatcher: () => {},
        markTaskWatcherLaunchSucceeded: () => {},
        hideProgressBar: () => {},
        showTaskErrorLog: (message) => statuses.push({ message, level: "error-log" }),
        openTaskModal: (taskType, label, btnId, options) => {
            sandbox.currentTaskModal = {
                taskType,
                label,
                btnId,
                mode: "PROGRESS",
                generation: 1,
                pipelineMode: options && options.pipelineMode === true,
                executionId: null
            };
        },
        fetch: async () => ({ ok: true, status: 204, text: async () => "", json: async () => ({}) }),
        setInterval: (fn, ms) => {
            const handle = { fn, ms, cleared: false };
            setIntervalCalls.push(handle);
            return handle;
        },
        clearInterval: (handle) => {
            if (handle) handle.cleared = true;
            clearedIntervals.push(handle);
        },
        setTimeout: (fn) => ({ fn }),
        clearTimeout: () => {},
        AbortController,
        URLSearchParams,
        api: async (requestPath, options) => {
            apiCalls.push({ path: requestPath, options: options || {} });
            if (typeof sandbox.apiHandler === "function") return sandbox.apiHandler(requestPath, options || {});
            return {};
        },
        $: (selector) => {
            if (!elements[selector]) elements[selector] = makeElement();
            return elements[selector];
        },
        $$: () => sandbox.sourceCheckboxes
    };
    Object.assign(sandbox, overrides);
    sandbox.elements = elements;
    sandbox.apiCalls = apiCalls;
    sandbox.boundExecutions = boundExecutions;
    sandbox.notifications = notifications;
    sandbox.statuses = statuses;
    sandbox.setIntervalCalls = setIntervalCalls;
    sandbox.clearedIntervals = clearedIntervals;
    sandbox.sourceCheckboxes = sandbox.sourceCheckboxes || [];
    return sandbox;
}

/** 载入 c3 段 + 被测函数；同一脚本内导出内部绑定，避免跨脚本读取 const/let。 */
function loadSandbox(overrides = {}, fnNames = []) {
    const sandbox = createSandbox(overrides);
    vm.createContext(sandbox);
    const epilogue = `
;globalThis.__c3 = {
    setMode: (mode) => { discoveryPipelineMode = mode; },
    mode: () => discoveryPipelineMode,
    sourcesState: () => discoverySources,
    metricsText: (status, envelope) => discoveryPipelineMetricsText(status, envelope),
    stateClass: (state, hasFailures) => pipelineStatusClass(state, hasFailures),
    stateLabel: (state, hasFailures) => pipelineStatusLabel(state, hasFailures),
    statusPollMs: PIPELINE_STATUS_POLL_MS,
    launchTimeoutMs: PIPELINE_LAUNCH_TIMEOUT_MS,
    statusTimeoutMs: PIPELINE_STATUS_TIMEOUT_MS,
    sourceTimeoutMs: DISCOVERY_SOURCE_TIMEOUT_MS,
    continuousDesc: CONTINUOUS_DISCOVERY_DESC
};`;
    const source = [
        c3Section(),
        ...fnNames.map(extractFn),
        epilogue
    ].join("\n");
    vm.runInContext(source, sandbox);
    return sandbox;
}

const filledTrack = () => {
    const fill = makeElement();
    fill.parentElement = { hidden: false };
    return { fill, track: fill.parentElement };
};

describe("c3 deep discovery continuous run (frontend)", () => {
    describe("I-6 per-task run lock", () => {
        it("allows deep discovery and check replies to coexist in both directions", async () => {
            for (const requested of ["EXPERT_DISCOVERY", "CHECK_REPLIES"]) {
                const running = requested === "EXPERT_DISCOVERY" ? "CHECK_REPLIES" : "EXPERT_DISCOVERY";
                const sandbox = loadSandbox({
                    fetch: async (url) => {
                        const type = url.split("/api/task-progress/")[1];
                        return type === running
                            ? { ok: true, status: 200, json: async () => ({ status: "RUNNING" }) }
                            : { ok: true, status: 204, json: async () => ({}) };
                    }
                }, ["progressStoreHasRunningTask", "fetchTaskRunningOrThrow", "isTaskRunning"]);
                assert.strictEqual(await sandbox.progressStoreHasRunningTask(requested), false,
                    `${requested} must be allowed while ${running} runs`);
            }
        });

        it("still blocks the same type and every other combination", async () => {
            const sameType = loadSandbox({
                fetch: async () => ({ ok: true, status: 200, json: async () => ({ status: "RUNNING" }) })
            }, ["progressStoreHasRunningTask", "fetchTaskRunningOrThrow", "isTaskRunning"]);
            assert.strictEqual(await sameType.progressStoreHasRunningTask("EXPERT_DISCOVERY"), true,
                "the same task type must never run twice");

            const rawScan = loadSandbox({
                fetch: async (url) => url.endsWith("/RAW_PROMOTION_SCAN")
                    ? { ok: true, status: 200, json: async () => ({ status: "RUNNING" }) }
                    : { ok: true, status: 204, json: async () => ({}) }
            }, ["progressStoreHasRunningTask", "fetchTaskRunningOrThrow", "isTaskRunning"]);
            assert.strictEqual(await rawScan.progressStoreHasRunningTask("EXPERT_DISCOVERY"), true,
                "RAW promotion must keep excluding deep discovery");

            const enrichment = loadSandbox({
                fetch: async (url) => url.endsWith("/EXPERT_ENRICHMENT")
                    ? { ok: true, status: 200, json: async () => ({ status: "RUNNING" }) }
                    : { ok: true, status: 204, json: async () => ({}) }
            }, ["progressStoreHasRunningTask", "fetchTaskRunningOrThrow", "isTaskRunning"]);
            assert.strictEqual(await enrichment.progressStoreHasRunningTask("CHECK_REPLIES"), true,
                "enrichment must keep excluding check replies");
        });

        it("surfaces a failed state query instead of reporting an idle system", async () => {
            const sandbox = loadSandbox({
                fetch: async (url) => url.endsWith("/CHECK_REPLIES")
                    ? { ok: false, status: 500, statusText: "Server Error", json: async () => ({}) }
                    : { ok: true, status: 204, json: async () => ({}) }
            }, ["progressStoreHasRunningTask", "fetchTaskRunningOrThrow", "isTaskRunning"]);
            await assert.rejects(
                () => sandbox.progressStoreHasRunningTask("EXPERT_DISCOVERY"),
                /任务状态查询失败/
            );
        });

        it("keeps legacy no-argument callers tolerant of a failed query", async () => {
            const sandbox = loadSandbox({
                fetch: async () => { throw new Error("network down"); }
            }, ["progressStoreHasRunningTask", "fetchTaskRunningOrThrow", "isTaskRunning"]);
            assert.strictEqual(await sandbox.progressStoreHasRunningTask(), false);
            assert.strictEqual(await sandbox.isTaskRunning("EXPERT_DISCOVERY"), false);
        });
    });

    describe("I-1/I-2 continuous launch", () => {
        it("freezes the operator selections before switching views and posts with a finite timeout", async () => {
            const sandbox = loadSandbox({}, [
                "executeDiscover", "postDiscoveryLaunch", "getSelectedSources",
                "handleDiscoveryLaunchFailure", "progressStoreHasRunningTask", "fetchTaskRunningOrThrow"
            ]);
            sandbox.__c3.setMode("CONTINUOUS");
            sandbox.$( "#taskLaunchKeywordInput" ).value = " ai , ml ";
            sandbox.$( "#taskLaunchIncludeRawScan" ).checked = true;
            sandbox.sourceCheckboxes = [{ value: "OPENALEX" }];
            const keyboardInput = sandbox.$("#taskLaunchKeywordInput");
            sandbox.openTaskModal = () => {
                // 切视图会重置配置区：冻结后必须仍然用启动前的选择。
                keyboardInput.value = "";
                sandbox.sourceCheckboxes = [];
                sandbox.currentTaskModal = {
                    taskType: "EXPERT_DISCOVERY", generation: 1, mode: "PROGRESS",
                    pipelineMode: true, executionId: null
                };
            };
            sandbox.apiHandler = (requestPath) => {
                if (requestPath.startsWith("/api/expert-discovery/run")) {
                    return { mode: "CONTINUOUS", pipelineId: 1, state: "QUEUED", phase: "QUEUED", executionId: null, message: "已受理，等待执行" };
                }
                return { mode: "CONTINUOUS", configured: true, status: { state: "QUEUED", waitReasons: [] } };
            };
            sandbox.__c3.sourcesState().status = "ready";

            await sandbox.executeDiscover();

            const launch = sandbox.apiCalls.find(call => call.path.startsWith("/api/expert-discovery/run/by-keyword"));
            assert.ok(launch, "the keyword entry must be used with the frozen keywords");
            assert.ok(launch.path.includes("keywords=ai") && launch.path.includes("keywords=ml"),
                "frozen keywords must be submitted, got: " + launch.path);
            assert.ok(launch.path.includes("sources=OPENALEX"), "frozen sources must be submitted");
            assert.ok(launch.path.includes("includeRawScan=true"), "includeRawScan must be explicit");
            assert.strictEqual(launch.options.timeoutMs, sandbox.__c3.launchTimeoutMs);
            assert.strictEqual(sandbox.notifications.length, 0, "a 202 acceptance is not a completion");
        });

        it("renders acceptance for 202 and keeps tracking the pipeline instead of notifying", async () => {
            const sandbox = loadSandbox({}, [
                "executeDiscover", "postDiscoveryLaunch", "getSelectedSources",
                "handleDiscoveryLaunchFailure", "progressStoreHasRunningTask", "fetchTaskRunningOrThrow"
            ]);
            sandbox.__c3.setMode("CONTINUOUS");
            sandbox.__c3.sourcesState().status = "ready";
            const status = { state: "QUEUED", phase: "QUEUED", waitReasons: [], budget: null };
            sandbox.apiHandler = (requestPath) => requestPath.startsWith("/api/expert-discovery/run")
                ? { mode: "CONTINUOUS", pipelineId: 1, state: "QUEUED", executionId: null, message: "已受理，等待执行" }
                : { mode: "CONTINUOUS", configured: true, status };

            await sandbox.executeDiscover();

            assert.strictEqual(sandbox.$("#taskModalMessage").textContent, "已受理，等待执行");
            assert.strictEqual(sandbox.$("#taskModalStatus").textContent, "QUEUED");
            assert.strictEqual(sandbox.$("#taskModalStatus").className, "task-modal-status running");
            assert.strictEqual(sandbox.$("#taskModalCancelBtn").textContent, "暂停发现");
            assert.strictEqual(sandbox.notifications.length, 0);
            const poll = sandbox.setIntervalCalls.find(call => call.ms === sandbox.__c3.statusPollMs);
            assert.ok(poll, "the pipeline status poll must be registered");

            // I-7：单次最多一个状态请求 —— 前一个未完成时的第二次 tick 必须直接跳过。
            await new Promise(resolve => setImmediate(resolve));
            const statusCalls = () => sandbox.apiCalls.filter(call => call.path === "/api/expert-discovery/pipeline").length;
            const before = statusCalls();
            const first = poll.fn();
            const second = poll.fn();
            assert.strictEqual(statusCalls(), before + 1, "only one status request may be in flight");
            await Promise.all([first, second]);
        });

        it("restores the retryable configuration when a different query is refused", async () => {
            const sandbox = loadSandbox({}, [
                "executeDiscover", "postDiscoveryLaunch", "getSelectedSources",
                "handleDiscoveryLaunchFailure", "progressStoreHasRunningTask", "fetchTaskRunningOrThrow"
            ]);
            sandbox.__c3.setMode("CONTINUOUS");
            sandbox.__c3.sourcesState().status = "ready";
            sandbox.apiHandler = (requestPath) => {
                if (requestPath.startsWith("/api/expert-discovery/run")) {
                    const error = new Error("已有不同的查询仍在推进");
                    error.status = 409;
                    throw error;
                }
                return {};
            };

            await sandbox.executeDiscover();

            assert.strictEqual(sandbox.$("#taskModalConfigSection").hidden, false,
                "the configuration section must be visible again for a retry");
            assert.strictEqual(sandbox.$("#taskModalProgressSection").hidden, true);
            assert.strictEqual(sandbox.$("#taskLaunchRunBtn").disabled, false);
            assert.ok(sandbox.$("#taskLaunchDesc").textContent.includes("已有不同的查询仍在推进"),
                "the conflict reason must be visible in the configuration area");
        });

        it("refuses to start when the source list never loaded", async () => {
            const sandbox = loadSandbox({}, [
                "executeDiscover", "postDiscoveryLaunch", "getSelectedSources",
                "handleDiscoveryLaunchFailure", "progressStoreHasRunningTask", "fetchTaskRunningOrThrow"
            ]);
            sandbox.__c3.sourcesState().status = "error";
            sandbox.__c3.sourcesState().error = "请求超时（10 秒）";
            sandbox.sourceCheckboxes = [];

            await sandbox.executeDiscover();

            assert.strictEqual(sandbox.apiCalls.filter(call => call.path.includes("/run")).length, 0,
                "a failed source load must not start discovery with default sources");
            assert.ok(sandbox.statuses.some(entry => entry.message.includes("来源")),
                "the failure must be visible to the operator");
        });
    });

    describe("I-5 honest status surface", () => {
        it("maps every state onto the existing status classes and hides the daily percentage", () => {
            const sandbox = loadSandbox({});
            const track = filledTrack();
            sandbox.elements["#taskModalFill"] = track.fill;
            sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 3, mode: "PROGRESS", pipelineMode: true };

            const expectations = [
                ["QUEUED", "running"],
                ["RUNNING", "running"],
                ["WAITING", "running"],
                ["PAUSED", "cancelled"],
                ["FAULTED", "failed"],
                ["DRAINED", "completed"]
            ];
            for (const [state, expected] of expectations) {
                vm.runInContext(
                    `renderDiscoveryPipelineStatus(${JSON.stringify({ state, phase: state, waitReasons: [], failedItems: 0 })}, 3);`,
                    sandbox
                );
                assert.strictEqual(sandbox.$("#taskModalStatus").textContent, state);
                assert.strictEqual(sandbox.$("#taskModalStatus").className, "task-modal-status " + expected,
                    state + " must reuse the existing status class");
                assert.strictEqual(sandbox.$("#taskModalPercent").textContent, "持续运行",
                    "continuous mode must never render a daily percentage");
            }

            vm.runInContext(
                `renderDiscoveryPipelineStatus(${JSON.stringify({ state: "DRAINED", phase: "DRAINED", waitReasons: [], failedItems: 2 })}, 3);`,
                sandbox
            );
            assert.strictEqual(sandbox.$("#taskModalStatus").className, "task-modal-status failed");
            assert.ok(sandbox.$("#taskModalStatus").textContent.includes("存在失败"),
                "a drained pipeline with failures must not render as success");
        });

        it("shows real numbers, 待同步 for unsynced budget and tolerates unknown wait reasons", () => {
            const sandbox = loadSandbox({});
            const status = {
                state: "WAITING",
                phase: "WAITING",
                queuedPapers: 70,
                processedPapers: 40,
                queuedRecords: 20,
                processedRecords: 10,
                queueDepth: 90,
                runningJobs: 3,
                indexedExperts: 8,
                duplicateExperts: 12,
                failedItems: 0,
                waitReasons: ["DAILY_BUDGET", "SEARCH_FAILED"],
                sources: [{ source: "OPENALEX", cursorState: "ACTIVE", queuedItems: 1, processedItems: 2, activeJobs: 1, failedJobs: 0, indexedExperts: 3, sourceError: null }],
                budget: {
                    lastSyncedAt: "2026-09-22T23:00:00Z",
                    confirmedSpentCredits: 227,
                    officialLimitCredits: 10000,
                    reservedCredits: 2,
                    effectiveRemainingCredits: 9771,
                    resetAt: "2026-09-23T00:00:00Z"
                }
            };
            const serverTexts = ["OpenAlex 官方额度已用尽，等待北京时间 2026-09-23 08:00 重置", "SEARCH_FAILED"];
            const text = sandbox.__c3.metricsText(status, { waitTexts: serverTexts });

            assert.ok(text.includes("论文：在队列 70 / 已处理 40"), text);
            assert.ok(text.includes("ORCID：在队列 20 / 已处理 10"), text);
            assert.ok(text.includes("专家：新增 8 / 重复 12"), text);
            assert.ok(text.includes("已用 227 / 上限 10000 credits"), text);
            assert.ok(text.includes("保护后可用 9771"), text);
            assert.ok(text.includes("北京时间 2026-09-23 08:00"), text);
            assert.ok(text.includes("SEARCH_FAILED"), "unknown reasons must pass through, not crash");

            const unsynced = sandbox.__c3.metricsText({
                state: "WAITING", waitReasons: [],
                budget: { lastSyncedAt: null, confirmedSpentCredits: 0, officialLimitCredits: null, reservedCredits: 0, effectiveRemainingCredits: 0 }
            }, null);
            assert.ok(unsynced.includes("待同步"), "unsynchronised budget must not be rendered as 0: " + unsynced);
            assert.ok(unsynced.includes("SEARCH_FAILED") === false);
        });

        it("binds only a non-null currentExecutionId from the status snapshot", () => {
            const sandbox = loadSandbox({});
            sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 4, mode: "PROGRESS", pipelineMode: true, executionId: null };

            vm.runInContext(
                `renderDiscoveryPipelineStatus(${JSON.stringify({ state: "WAITING", phase: "WAITING", waitReasons: [], currentExecutionId: null })}, 4);`,
                sandbox
            );
            assert.deepStrictEqual(sandbox.boundExecutions, [],
                "a null executionId must never bind the latest historical task");

            vm.runInContext(
                `renderDiscoveryPipelineStatus(${JSON.stringify({ state: "RUNNING", phase: "RUNNING", waitReasons: [], currentExecutionId: 88 })}, 4);`,
                sandbox
            );
            assert.deepStrictEqual(sandbox.boundExecutions, [
                { taskType: "EXPERT_DISCOVERY", generation: 4, executionId: 88 }
            ]);
        });

        it("never renders into a closed or switched dialog", () => {
            const sandbox = loadSandbox({});
            sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 9, mode: "PROGRESS", pipelineMode: true, executionId: null };

            vm.runInContext(
                `renderDiscoveryPipelineStatus(${JSON.stringify({ state: "FAULTED", phase: "FAULTED", waitReasons: ["SOURCE_ERROR"] })}, 8);`,
                sandbox
            );
            assert.strictEqual(sandbox.$("#taskModalStatus").textContent, "",
                "a stale generation must never overwrite the current dialog");
            assert.deepStrictEqual(sandbox.boundExecutions, []);
        });
    });

    describe("I-3/S-2 pause and resume", () => {
        const cancelButton = (sandbox) => sandbox.$("#taskModalCancelBtn");

        it("pauses a running pipeline through the persisted cancel endpoint", async () => {
            const sandbox = loadSandbox({}, ["handleCancelTask"]);
            sandbox.currentTaskModal = {
                taskType: "EXPERT_DISCOVERY", generation: 2, mode: "PROGRESS",
                pipelineMode: true, pipelineState: "RUNNING"
            };
            await sandbox.handleCancelTask();

            assert.strictEqual(sandbox.apiCalls[0].path, "/api/task-progress/EXPERT_DISCOVERY/cancel");
            assert.strictEqual(cancelButton(sandbox).disabled, false);
            assert.strictEqual(cancelButton(sandbox).textContent, "暂停发现");
        });

        it("resumes an explicitly paused pipeline instead of cancelling it", async () => {
            const sandbox = loadSandbox({}, ["handleCancelTask"]);
            sandbox.currentTaskModal = {
                taskType: "EXPERT_DISCOVERY", generation: 2, mode: "PROGRESS",
                pipelineMode: true, pipelineState: "PAUSED"
            };
            await sandbox.handleCancelTask();

            assert.strictEqual(sandbox.apiCalls[0].path, "/api/expert-discovery/pipeline/resume");
            assert.strictEqual(cancelButton(sandbox).textContent, "恢复发现",
                "a paused pipeline must offer 恢复发现 again");
        });

        it("disables the button while the pause request is pending", async () => {
            let release = null;
            const sandbox = loadSandbox({
                api: (requestPath, options) => new Promise(resolve => { release = () => resolve({ state: "PAUSED" }); })
            }, ["handleCancelTask"]);
            sandbox.currentTaskModal = {
                taskType: "EXPERT_DISCOVERY", generation: 2, mode: "PROGRESS",
                pipelineMode: true, pipelineState: "RUNNING"
            };
            const pending = sandbox.handleCancelTask();
            await Promise.resolve();

            assert.strictEqual(cancelButton(sandbox).disabled, true);
            assert.strictEqual(cancelButton(sandbox).textContent, "暂停中...");

            release();
            await pending;
            assert.strictEqual(cancelButton(sandbox).disabled, false);
            assert.strictEqual(cancelButton(sandbox).textContent, "暂停发现");
        });

        it("keeps the legacy cancel wording for other task types", async () => {
            const sandbox = loadSandbox({}, ["handleCancelTask"]);
            sandbox.currentTaskModal = { taskType: "EXPERT_REVALIDATION", generation: 2, mode: "PROGRESS" };
            await sandbox.handleCancelTask();

            assert.strictEqual(sandbox.apiCalls[0].path, "/api/task-progress/EXPERT_REVALIDATION/cancel");
            assert.strictEqual(cancelButton(sandbox).textContent, "取消任务");
        });
    });

    describe("I-7 source loading", () => {
        it("reports a load failure with a retry hint and leaves no stale selection", async () => {
            const sandbox = loadSandbox({
                api: async () => {
                    const error = new Error("请求超时（10 秒）");
                    throw error;
                }
            }, ["loadDiscoverySources"]);
            sandbox.$("#taskLaunchSources").innerHTML = "<label>stale</label>";

            await assert.rejects(() => sandbox.loadDiscoverySources(), /来源加载失败：请求超时（10 秒）；请重新打开弹窗重试/);
            assert.strictEqual(sandbox.__c3.sourcesState().status, "error");
            assert.strictEqual(sandbox.$("#taskLaunchSources").innerHTML, "",
                "a failed load must clear the previous selection");
        });

        it("marks itself ready with the operator's defaults when the request succeeds", async () => {
            const sandbox = loadSandbox({
                api: async () => ([
                    { sourceName: "OPENALEX", enabled: true, extractionMethod: "FULLTEXT_XML" },
                    { sourceName: "EUROPE_PMC", enabled: false, extractionMethod: "FULLTEXT_XML" }
                ])
            }, ["loadDiscoverySources"]);

            const sources = await sandbox.loadDiscoverySources();

            assert.strictEqual(sources.length, 2);
            assert.strictEqual(sandbox.__c3.sourcesState().status, "ready");
            assert.ok(sandbox.elements["#taskLaunchSources"].innerHTML.includes("OPENALEX"));
            assert.ok(sandbox.elements["#taskLaunchSources"].innerHTML.includes("disabled"),
                "sources excluded by the scope must render as disabled, not checked");
        });
    });

    describe("S-1/S-2 node contract", () => {
        it("initializes the same nodes for continuous mode and restores the legacy display afterwards", () => {
            const sandbox = loadSandbox({
                createTaskModalContext: (taskType, label, btnId, mode) => ({
                    taskType, label, btnId, mode, generation: 1, executionId: null
                }),
                fetchRunList: () => {},
                fetchAndCacheBatchLogs: () => {},
                stopBatchSendStatusPoll: () => {},
                document: { body: { classList: { add: () => {}, remove: () => {} } } }
            }, ["openTaskModal"]);
            const fill = makeElement();
            fill.parentElement = { hidden: false };
            sandbox.$("#taskModalFill");
            sandbox.elements["#taskModalFill"] = fill;

            sandbox.openTaskModal("EXPERT_DISCOVERY", "深度发现（外部数据源）", null, { pipelineMode: true });
            assert.strictEqual(sandbox.$("#taskModalPercent").textContent, "持续运行",
                "continuous mode must not show a daily percentage");
            assert.strictEqual(sandbox.$("#taskModalStatus").textContent, "QUEUED");
            assert.strictEqual(sandbox.$("#taskModalStatus").className, "task-modal-status running");
            assert.strictEqual(sandbox.$("#taskModalMessage").textContent, "已受理，等待执行");
            assert.strictEqual(sandbox.$("#taskModalCancelBtn").textContent, "暂停发现");
            assert.strictEqual(fill.parentElement.hidden, true, "the daily track must be hidden in continuous mode");
            assert.strictEqual(sandbox.setIntervalCalls.filter(call => call.ms === 1000).length, 0,
                "continuous mode must not poll the legacy task-progress endpoint");
            assert.strictEqual(sandbox.setIntervalCalls.filter(call => call.ms === 5000).length, 1,
                "window history must keep loading through the existing run list");

            sandbox.setIntervalCalls.length = 0;
            sandbox.openTaskModal("EXPERT_REVALIDATION", "重新验证", null, {});
            assert.strictEqual(sandbox.$("#taskModalPercent").textContent, "0%");
            assert.strictEqual(sandbox.$("#taskModalStatus").textContent, "RUNNING");
            assert.strictEqual(sandbox.$("#taskModalCancelBtn").textContent, "取消任务");
            assert.strictEqual(fill.parentElement.hidden, false, "legacy tasks must get the daily track back");
            assert.strictEqual(sandbox.setIntervalCalls.filter(call => call.ms === 1000).length, 1,
                "legacy tasks keep their 1s progress poll");
        });

        it("keeps using the existing status nodes and the existing track element", () => {
            for (const id of [
                "taskModalProgressSection", "taskModalStatus", "taskModalPercent", "taskModalFill",
                "taskModalMessage", "taskModalBySource", "taskModalBySourceContent",
                "taskModalCancelBtn", "taskLaunchDesc", "taskLaunchRunBtn", "taskLaunchSources"
            ]) {
                assert.ok(html.includes(`id="${id}"`), `#${id} must exist in index.html (DOM stub tests cannot catch this)`);
            }
            assert.ok(html.includes('class="task-progress-track"'),
                "the daily progress track must stay in index.html (continuous mode only hides it)");
            assert.ok(appJsSource.includes('cancelBtn.textContent = state === "PAUSED" ? "恢复发现" : "暂停发现"'),
                "the pause/resume text must be applied to the existing cancel button");
        });
    });
});
