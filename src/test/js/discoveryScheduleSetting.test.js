// c4（I-1/I-2/I-3 + S-1/S-2）：深度发现「执行间隔（小时）」设置面板的前端行为与契约。
//
// 本文件的沙箱载入**生产源码**：运行时的 modal context/generation、c3 深度发现段（含
// formatBeijingInstant 与 DISCOVERY_TASK_TYPE）、c4 的常量段，以及真实的
// openTaskModal / closeTaskModal / openTaskLaunchModal / executeDiscover 与被测的
// 设置面板函数；只对 API、DOM、计时器与无关的渲染接缝做隔离桩。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsSource = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");
const indexHtml = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");
const stylesCss = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");
const runtimeSource = fs.readFileSync(path.join(staticDir, "task-modal-runtime.js"), "utf-8");

// vm 沙箱单测以函数为单位抽取源码（与既有 taskModalStateMachine.test.js 同惯例）。
function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

/** c3/c4 的常量与状态是连续源码段；整段载入保证测的就是生产常量，而不是测试副本。 */
function sectionBetween(startMarker, endMarker, label) {
    const start = appJsSource.indexOf(startMarker);
    const end = appJsSource.indexOf(endMarker);
    assert.ok(start > 0 && end > start, label + " must stay in app.js");
    return appJsSource.slice(start, end);
}

const DISCOVERY_SECTION = sectionBetween(
    "const DISCOVERY_TASK_TYPE",
    "function isCurrentTaskWatcher",
    "the c3 discovery section"
);
const SCHEDULE_SECTION = sectionBetween(
    "const DISCOVERY_SCHEDULE_PATH",
    "function discoveryScheduleElements",
    "the c4 schedule constants"
);

/** 被测的生产函数（extractFn 找不到即直接失败，等价于源码契约断言）。 */
const SCHEDULE_FUNCTIONS = [
    "discoveryScheduleElements",
    "renderDiscoveryScheduleView",
    "resetDiscoverySchedulePanel",
    "initDiscoverySchedulePanel",
    "saveDiscoverySchedule"
];

const DISCOVERY_PATH = "/api/expert-discovery/schedule";
const PREVIOUS_CACHE_KEY = "20260929-mailbox-replied";
const flush = () => new Promise((resolve) => setImmediate(resolve));

const OVERVIEW_VIEW = {
    mode: "LEGACY",
    editable: true,
    source: "CONFIG",
    intervalHours: 2,
    anchorAt: null,
    nextTriggerAt: null,
    applied: true,
    saved: false,
    reason: null,
    message: "当前沿用系统定时（部署 cron）"
};

const APPLIED_OVERRIDE_VIEW = {
    mode: "LEGACY",
    editable: true,
    source: "OVERRIDE",
    intervalHours: 3,
    anchorAt: "2026-09-29T06:00:00Z",
    nextTriggerAt: "2026-09-29T14:00:00Z",
    applied: true,
    saved: true,
    reason: null,
    message: "已设置每 3 小时执行一次"
};

const OVERVIEW_HINT = "当前沿用系统定时：每 2 小时整点尝试执行。保存后按保存时间重新计时。";
const INVALID_HOURS_HINT = "请输入 1～168 的整数小时";

function makeElement() {
    const listeners = {};
    return {
        textContent: "",
        innerHTML: "",
        className: "",
        hidden: false,
        disabled: false,
        checked: false,
        value: "",
        style: {},
        parentElement: null,
        listeners,
        addEventListener(type, handler) {
            (listeners[type] = listeners[type] || []).push(handler);
        },
        click() {
            (listeners.click || []).slice().forEach((handler) => handler());
        }
    };
}

function makeSandbox(overrides = {}) {
    const elements = {};
    const apiCalls = [];
    const statuses = [];
    const intervals = [];
    const sandbox = {
        console,
        contextPath: "/subpath",
        sourceCheckboxes: [],
        document: { body: { classList: { add() {}, remove() {} } }, querySelector: () => null },
        $: (selector) => {
            if (!elements[selector]) elements[selector] = makeElement();
            return elements[selector];
        },
        $$: (selector) => (selector.includes("source-cb") ? sandbox.sourceCheckboxes : []),
        showStatus: (message, level) => statuses.push({ message, level }),
        showTaskErrorLog: (message) => statuses.push({ message, level: "error-log" }),
        hideProgressBar: () => {},
        handleAuthResponse: async () => {},
        AbortController,
        URLSearchParams,
        notifyTaskCompletionOnce: () => {},
        bindTaskModalExecution: async () => {},
        updateTaskModalFromProgress: () => {},
        updateTaskModalLogs: () => {},
        fetchRunList: () => {},
        stopTaskModalPolling: () => {},
        stopTaskWatcher: () => {},
        startTaskWatcher: () => {},
        markTaskWatcherLaunchSucceeded: () => {},
        stopBatchSendStatusPoll: () => {},
        progressStoreHasRunningTask: async () => false,
        fetch: async () => ({ ok: true, status: 200, json: async () => ({ status: "RUNNING", percent: 40 }) }),
        setInterval: (fn, ms) => {
            const handle = { fn, ms, cleared: false };
            intervals.push(handle);
            return handle;
        },
        clearInterval: (handle) => {
            if (handle) handle.cleared = true;
        },
        setTimeout: (fn) => ({ fn }),
        clearTimeout: () => {},
        api: async (requestPath, options) => {
            apiCalls.push({ path: requestPath, options: options || {} });
            if (typeof sandbox.apiHandler === "function") return sandbox.apiHandler(requestPath, options || {});
            return {};
        }
    };
    Object.assign(sandbox, overrides);
    sandbox.elements = elements;
    sandbox.apiCalls = apiCalls;
    sandbox.statuses = statuses;
    sandbox.intervals = intervals;
    sandbox.sourceCheckboxes = sandbox.sourceCheckboxes || [];
    return sandbox;
}

function loadSandbox(overrides = {}, fnNames = []) {
    const sandbox = makeSandbox(overrides);
    vm.createContext(sandbox);
    vm.runInContext(runtimeSource, sandbox);
    vm.runInContext(DISCOVERY_SECTION, sandbox);
    vm.runInContext(SCHEDULE_SECTION, sandbox);
    vm.runInContext(SCHEDULE_FUNCTIONS.concat(fnNames).map(extractFn).join("\n"), sandbox);
    return sandbox;
}

const putCalls = (sandbox) => sandbox.apiCalls.filter((call) => call.options && call.options.method === "PUT");
const scheduleGetCalls = (sandbox) =>
    sandbox.apiCalls.filter((call) => call.path === DISCOVERY_PATH && (!call.options || call.options.method !== "PUT"));

/** 打开路径共用：真实 openTaskModal（PROGRESS）或真实 openTaskLaunchModal（CONFIG）。 */
async function openDiscoveryModal(sandbox, options = {}) {
    sandbox.openTaskModal("EXPERT_DISCOVERY", "深度发现（外部数据源）", null, options);
    await flush();
}

function scheduleSandbox(options = {}) {
    const view = options.view === undefined ? OVERVIEW_VIEW : options.view;
    return loadSandbox({
        apiHandler: (requestPath, requestOptions) => {
            if (requestPath !== DISCOVERY_PATH) return {};
            if (requestOptions.method === "PUT") {
                if (options.putError) return Promise.reject(options.putError);
                if (typeof options.putImpl === "function") return options.putImpl(requestOptions);
                return options.putView === undefined ? view : options.putView;
            }
            if (options.getError) return Promise.reject(options.getError);
            if (typeof options.getImpl === "function") return options.getImpl(requestOptions);
            return view;
        }
    }, ["openTaskModal", "closeTaskModal", "openTaskLaunchModal"]);
}

describe("c4 discovery schedule setting (frontend)", () => {
    describe("S-1/S-2 contract", () => {
        it("keeps the S-1 styles verbatim and the existing task-modal rules untouched", () => {
            const s1Css = `.discovery-schedule-panel {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
}
.discovery-schedule-row {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 8px;
}
.discovery-schedule-hours {
    width: 120px;
}
.discovery-schedule-hint {
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--text-secondary);
}
.discovery-schedule-save:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
}
.discovery-schedule-save:disabled,
.discovery-schedule-save:disabled:hover,
.discovery-schedule-save:disabled:active {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
    background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
}
.discovery-schedule-hours:disabled {
    opacity: 0.65;
    cursor: not-allowed;
}
`;
            assert.ok(stylesCss.includes(s1Css), "the S-1 CSS block must be copied verbatim");

            for (const baseline of [
                `.task-modal-input-label {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-main);
    display: flex;
    flex-direction: column;
    gap: 6px;
}`,
                `.task-modal-input-field {
    width: 100%;
    padding: 8px 12px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    background-color: var(--surface);
    color: var(--text-main);
    font-family: inherit;
    font-size: 13px;
    transition: border-color 0.2s, box-shadow 0.2s;
}`,
                `.task-modal-input-field:focus {
    outline: none;
    border-color: var(--primary);
    box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1);
}`
            ]) {
                assert.ok(stylesCss.includes(baseline), "existing task-modal rules must stay byte-identical");
            }
        });

        it("declares the S-1 DOM before #taskModalConfigSection and stays style/onclick free", () => {
            const s1DomLines = [
                '<div id="discoverySchedulePanel" class="discovery-schedule-panel" hidden>',
                '<div id="discoveryScheduleControls" class="discovery-schedule-row">',
                '<label for="discoveryScheduleHours" class="task-modal-input-label">',
                "执行间隔（小时）",
                '<input id="discoveryScheduleHours" class="task-modal-input-field discovery-schedule-hours"',
                'type="number" min="1" max="168" step="1"',
                'aria-describedby="discoveryScheduleHint" disabled>',
                "</label>",
                '<button id="discoveryScheduleSave" class="button primary discovery-schedule-save"',
                'type="button" disabled>保存定时</button>',
                "</div>",
                '<p id="discoveryScheduleHint" class="discovery-schedule-hint" role="status" aria-live="polite"></p>',
                "</div>"
            ];
            const panelAt = indexHtml.indexOf(s1DomLines[0]);
            assert.ok(panelAt > 0, "index.html must declare #discoverySchedulePanel");
            let cursor = panelAt;
            for (const line of s1DomLines) {
                const at = indexHtml.indexOf(line, cursor);
                assert.ok(at >= 0, "missing S-1 DOM line: " + line);
                cursor = at + line.length;
            }
            const modalBodyAt = indexHtml.indexOf('class="modal-body"');
            const configAt = indexHtml.indexOf('id="taskModalConfigSection"');
            assert.ok(modalBodyAt > 0 && modalBodyAt < panelAt, "the panel must be a .modal-body child");
            assert.ok(panelAt < configAt, "the panel must precede #taskModalConfigSection");

            const panelBlock = indexHtml.slice(panelAt, cursor);
            assert.ok(!panelBlock.includes("style="), "no inline styles on the new nodes");
            assert.ok(!panelBlock.includes("onclick"), "no inline onclick on the new nodes");

            // DOM stub tests cannot catch dangling ids (K-dom-stub-tests-hide-dangling-refs).
            for (const id of [
                "discoverySchedulePanel",
                "discoveryScheduleControls",
                "discoveryScheduleHours",
                "discoveryScheduleSave",
                "discoveryScheduleHint"
            ]) {
                assert.ok(indexHtml.includes(`id="${id}"`), `#${id} must exist in index.html`);
                assert.ok(appJsSource.includes(`#${id}`), `#${id} must be addressed by app.js`);
            }
        });

        it("keeps every versioned asset on one non-legacy key (S-2)", () => {
            const keys = [...indexHtml.matchAll(/\?v=([^"'&<>]+)/g)].map((match) => match[1]);
            assert.strictEqual(keys.length, 11, "index.html must keep exactly the 11 versioned assets");
            assert.strictEqual(new Set(keys).size, 1, "all versioned keys must share one value: " + keys.join(", "));
            const cacheKey = keys[0];
            assert.match(cacheKey, /^[0-9]{8}-[a-z0-9-]+$/, "the shared key must be <yyyymmdd>-<slug>");
            assert.notStrictEqual(cacheKey, PREVIOUS_CACHE_KEY, "the released key must be bumped");
            for (const asset of [
                "styles.css", "expert-materials.css", "mailbox-chat.css", "meeting-confirmation.css",
                "world-clock.css", "trust-reply-workbench.js", "expert-materials.js",
                "meeting-confirmation.js", "mailbox-chat.js", "app.js", "world-clock.js"
            ]) {
                assert.ok(indexHtml.includes(`${asset}?v=${cacheKey}`), `${asset} must carry the shared key`);
            }
            assert.ok(!indexHtml.includes(PREVIOUS_CACHE_KEY), "the previous key must have zero hits");
        });

        it("keeps the schedule module on the approved seam", () => {
            for (const name of SCHEDULE_FUNCTIONS) {
                const source = extractFn(name);
                assert.ok(!source.includes("innerHTML"), name + " must render through textContent only");
                assert.ok(!source.includes("localStorage"), name + " must not keep a second truth in the browser");
            }
            const saveSource = extractFn("saveDiscoverySchedule");
            assert.ok(saveSource.includes("DISCOVERY_SCHEDULE_PATH"), "the only write path is the schedule endpoint");
            for (const forbidden of ["/run", "/pipeline", "/start", "/resume", "/discover", "keywords"]) {
                assert.ok(!saveSource.includes(forbidden), "保存定时 must not touch " + forbidden);
            }
            const initSource = extractFn("initDiscoverySchedulePanel");
            assert.ok(initSource.includes("isCurrentTaskModal"), "异步结果必须按弹窗代次校验");
        });
    });

    describe("I-1/I-2 open, read and lifecycle", () => {
        it("reads once when the running task modal opens and never saves on open (I-1/I-2)", async () => {
            const sandbox = scheduleSandbox({});
            await openDiscoveryModal(sandbox, { knownActiveAtOpen: true });

            assert.strictEqual(scheduleGetCalls(sandbox).length, 1, "opening reads the schedule exactly once");
            assert.strictEqual(putCalls(sandbox).length, 0, "opening must never save");
            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleControls").hidden, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, OVERVIEW_HINT);
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "2");
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false);
        });

        it("reads once from the idle launch entry and keeps 2 hours as the default", async () => {
            const sandbox = loadSandbox({
                taskLaunchConfigs: {
                    EXPERT_DISCOVERY: {
                        title: "深度发现（外部数据源）",
                        desc: "从外部数据源搜索并导入新专家到系统中。",
                        btnId: "discoverBtn",
                        showKeyword: true,
                        showMaxPromotions: false,
                        preload: null,
                        run: async () => {}
                    }
                },
                apiHandler: (requestPath) => {
                    if (requestPath === "/api/expert-discovery/pipeline") return { mode: "LEGACY" };
                    if (requestPath === DISCOVERY_PATH) return OVERVIEW_VIEW;
                    return {};
                }
            }, ["openTaskLaunchModal"]);

            await sandbox.openTaskLaunchModal("EXPERT_DISCOVERY");
            await flush();

            assert.strictEqual(scheduleGetCalls(sandbox).length, 1);
            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "2");
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, OVERVIEW_HINT);
        });

        it("shows loading but blocks saving until the read returns (I-3)", async () => {
            let resolveGet;
            const pending = new Promise((resolve) => { resolveGet = resolve; });
            const sandbox = scheduleSandbox({ getImpl: () => pending });
            await openDiscoveryModal(sandbox);

            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleControls").hidden, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "正在读取定时配置…");
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, true);
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").disabled, true);

            resolveGet(OVERVIEW_VIEW);
            await flush();
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, OVERVIEW_HINT);
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false);
        });

        it("fails closed on a read failure and never fakes a savable state (I-3)", async () => {
            const sandbox = scheduleSandbox({
                getError: Object.assign(new Error("503 Service Unavailable"), {
                    status: 503,
                    data: { reason: "DB_UNAVAILABLE", message: "定时设置库暂不可用，请稍后重试" }
                })
            });
            await openDiscoveryModal(sandbox);

            assert.strictEqual(
                sandbox.$("#discoveryScheduleHint").textContent,
                "定时配置加载失败，请重新打开弹窗重试（定时设置库暂不可用，请稍后重试）"
            );
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, true);
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").disabled, true);
            assert.strictEqual(sandbox.$("#discoveryScheduleControls").hidden, false, "控件可见但禁用");
            assert.strictEqual(putCalls(sandbox).length, 0);
        });

        it("hides the panel for other tasks and ignores the stale read result (I-2)", async () => {
            let resolveFirst;
            const first = new Promise((resolve) => { resolveFirst = resolve; });
            let reads = 0;
            const sandbox = scheduleSandbox({
                getImpl: () => {
                    reads += 1;
                    return reads === 1 ? first : OVERVIEW_VIEW;
                }
            });
            await openDiscoveryModal(sandbox);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "正在读取定时配置…");

            sandbox.openTaskModal("EXPERT_REVALIDATION", "重新验证候选人", null, {});
            await flush();
            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, true, "other tasks never show the panel");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "");

            resolveFirst(APPLIED_OVERRIDE_VIEW);
            await flush();
            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, true, "the stale read must not reopen the panel");
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "", "the stale read must not write the hint");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "");
        });

        it("never lets the progress poll overwrite the hours being typed (I-2)", async () => {
            const sandbox = scheduleSandbox({});
            await openDiscoveryModal(sandbox, { knownActiveAtOpen: true });

            const poll = sandbox.intervals.find((handle) => handle.ms === 1000);
            assert.ok(poll, "a running legacy task still installs the 1s progress poll");
            sandbox.$("#discoveryScheduleHours").value = "7";
            sandbox.$("#discoveryScheduleHint").textContent = "正在输入";

            await poll.fn();
            await flush();

            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "7", "the poll must not touch the input");
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "正在输入");
        });

        it("closing hides the panel, invalidates the in-flight PUT and never cancels the background task (I-1/I-2)", async () => {
            let resolvePut;
            const pendingPut = new Promise((resolve) => { resolvePut = resolve; });
            const watcherCalls = [];
            const sandbox = scheduleSandbox({ putImpl: () => pendingPut });
            sandbox.startTaskWatcher = (taskType, options) => watcherCalls.push({ taskType, options });

            await openDiscoveryModal(sandbox, { knownActiveAtOpen: true });
            sandbox.$("#discoveryScheduleHours").value = "5";
            sandbox.$("#discoveryScheduleSave").click();
            await flush();
            assert.strictEqual(putCalls(sandbox).length, 1);

            sandbox.closeTaskModal();

            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, true);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "");
            assert.strictEqual(sandbox.$("#taskProgressModal").hidden, true);
            assert.strictEqual(
                sandbox.apiCalls.filter((call) => call.path.includes("/cancel")).length, 0,
                "closing a modal must never cancel the running task"
            );
            assert.strictEqual(watcherCalls.length, 1, "the background watcher keeps tracking the running task");
            assert.strictEqual(watcherCalls[0].taskType, "EXPERT_DISCOVERY");

            resolvePut(APPLIED_OVERRIDE_VIEW);
            await flush();
            assert.strictEqual(sandbox.$("#discoverySchedulePanel").hidden, true, "the late PUT must not reopen the panel");
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "");
            assert.strictEqual(putCalls(sandbox).length, 1, "no further request may follow the close");
        });

        it("manual 立即执行 keeps its own payload and never saves the schedule (I-1)", async () => {
            const launchCalls = [];
            const openedWith = [];
            const sandbox = loadSandbox({
                sourceCheckboxes: [{ value: "OPENALEX", disabled: false }],
                openTaskModal: (taskType, label, btnId, options) => {
                    openedWith.push({ taskType, options });
                    sandbox.currentTaskModal = sandbox.createTaskModalContext(taskType, label, btnId, "PROGRESS");
                },
                apiHandler: (requestPath, options) => {
                    if (requestPath.startsWith("/api/expert-discovery/run")) {
                        launchCalls.push({ requestPath, options });
                        return { executionId: 7, resultSummary: JSON.stringify({ stats: {} }) };
                    }
                    return {};
                }
            }, [
                "executeDiscover", "postDiscoveryLaunch", "getSelectedSources", "handleDiscoveryLaunchFailure"
            ]);
            vm.runInContext('discoverySources.status = "ready";', sandbox);
            sandbox.$("#taskLaunchKeywordInput").value = "AI, ML";
            sandbox.$("#taskLaunchIncludeRawScan").checked = false;

            await sandbox.executeDiscover();
            await flush();

            assert.strictEqual(launchCalls.length, 1, "the manual launch still posts exactly once");
            assert.ok(launchCalls[0].requestPath.includes("keywords=AI"), "keywords stay frozen into the request");
            assert.ok(launchCalls[0].requestPath.includes("keywords=ML"));
            assert.ok(launchCalls[0].requestPath.includes("sources=OPENALEX"), "运营选中的来源原样发送");
            assert.strictEqual(openedWith.length, 1);
            assert.deepStrictEqual(sandbox.statuses.map((entry) => entry.level), ["ok"],
                "the launch succeeds without any failure status (runtime notifyTaskCompletionOnce)");
            assert.ok(sandbox.statuses[0].message.startsWith("专家发现完成"));
            assert.strictEqual(putCalls(sandbox).length, 0, "立即执行 must not PUT the schedule");
            assert.strictEqual(scheduleGetCalls(sandbox).length, 0, "立即执行 must not read the schedule either");
        });
    });

    describe("I-1/I-3 display rules", () => {
        it("renders the default schedule, the unrecognised cron and both override variants", async () => {
            const cases = [
                [
                    { source: "CONFIG", intervalHours: 2, applied: true, saved: false, nextTriggerAt: "2026-09-29T14:00:00Z" },
                    "2",
                    OVERVIEW_HINT
                ],
                [
                    { source: "CONFIG", intervalHours: null, applied: true, saved: false },
                    "",
                    "当前沿用系统定时；可设置整数小时周期。"
                ],
                [
                    { source: "OVERRIDE", intervalHours: 5, applied: true, saved: true, nextTriggerAt: "2026-09-29T14:00:00Z" },
                    "5",
                    "已设置每 5 小时执行一次。下次计划触发：2026-09-29 22:00（北京时间）；运行中将跳过。"
                ],
                [
                    { source: "OVERRIDE", intervalHours: 5, applied: true, saved: true, nextTriggerAt: null },
                    "5",
                    "已设置每 5 小时执行一次。下次时间暂不可用。"
                ]
            ];
            for (const [view, expectedHours, expectedHint] of cases) {
                const sandbox = scheduleSandbox({
                    view: Object.assign({ mode: "LEGACY", editable: true, anchorAt: null, reason: null, message: "x" }, view)
                });
                await openDiscoveryModal(sandbox);
                assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, expectedHours, expectedHint);
                assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, expectedHint);
                assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false);
            }
        });

        it("hides the hour controls with the server message when the mode is not settable (I-3)", async () => {
            const reasons = [
                ["CONTINUOUS_MODE", "当前为连续发现模式，小时周期设置不适用"],
                ["DISABLED", "系统定时发现已停用（enabled=false），小时周期设置不适用"],
                ["CRON_DISABLED", "定时 cron 已停用（cron=-），小时周期设置不适用"]
            ];
            for (const [reason, message] of reasons) {
                const sandbox = scheduleSandbox({
                    view: {
                        mode: reason === "CONTINUOUS_MODE" ? "CONTINUOUS" : "LEGACY",
                        editable: false, source: "CONFIG", intervalHours: null, anchorAt: null,
                        nextTriggerAt: null, applied: false, saved: false, reason, message
                    }
                });
                await openDiscoveryModal(sandbox);
                assert.strictEqual(sandbox.$("#discoveryScheduleControls").hidden, true, reason);
                assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, true, reason);
                assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, message, reason);
            }
        });

        it("never claims a saved value took effect when applied is false (I-1)", async () => {
            const sandbox = scheduleSandbox({
                view: {
                    mode: "LEGACY", editable: true, source: "CONFIG", intervalHours: 2, anchorAt: null,
                    nextTriggerAt: null, applied: false, saved: false, reason: "NOT_STARTED",
                    message: "定时尚未生效"
                }
            });
            await openDiscoveryModal(sandbox);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "当前沿用系统定时；可设置整数小时周期。");
        });
    });

    describe("I-1/I-3 saving", () => {
        it("sends one PUT carrying intervalHours alone and renders the applied response", async () => {
            const sandbox = scheduleSandbox({ putView: APPLIED_OVERRIDE_VIEW });
            await openDiscoveryModal(sandbox);

            sandbox.$("#discoveryScheduleHours").value = "3";
            assert.strictEqual(putCalls(sandbox).length, 0, "typing alone must never save");

            sandbox.$("#discoveryScheduleSave").click();
            await flush();

            const puts = putCalls(sandbox);
            assert.strictEqual(puts.length, 1);
            assert.strictEqual(puts[0].path, DISCOVERY_PATH);
            assert.deepStrictEqual(JSON.parse(puts[0].options.body), { intervalHours: 3 });
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent,
                "已设置每 3 小时执行一次。下次计划触发：2026-09-29 22:00（北京时间）；运行中将跳过。");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "3");
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false);
        });

        it("rejects non-integral and out-of-range input without any request (I-3)", async () => {
            const sandbox = scheduleSandbox({});
            await openDiscoveryModal(sandbox);

            for (const raw of ["", " ", "1.5", "0", "169", "abc", "-3", "1e3"]) {
                sandbox.$("#discoveryScheduleHint").textContent = "sentinel";
                sandbox.$("#discoveryScheduleHours").value = raw;
                sandbox.$("#discoveryScheduleSave").click();
                await flush();
                assert.strictEqual(putCalls(sandbox).length, 0, "no request for input: " + JSON.stringify(raw));
                assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, INVALID_HOURS_HINT,
                    "invalid input must explain the accepted range: " + JSON.stringify(raw));
                assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false, "the operator keeps retrying");
            }
        });

        it("disables the controls while saving and sends exactly one PUT for a double click (I-3)", async () => {
            let resolvePut;
            const pendingPut = new Promise((resolve) => { resolvePut = resolve; });
            const sandbox = scheduleSandbox({ putImpl: () => pendingPut });
            await openDiscoveryModal(sandbox);

            sandbox.$("#discoveryScheduleHours").value = "4";
            const button = sandbox.$("#discoveryScheduleSave");
            button.click();
            assert.strictEqual(button.disabled, true, "the save button is disabled while the PUT is in flight");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").disabled, true);
            button.click();
            await flush();
            assert.strictEqual(putCalls(sandbox).length, 1, "a second click must not submit a second PUT");

            resolvePut({
                mode: "LEGACY", editable: true, source: "OVERRIDE", intervalHours: 4, anchorAt: null,
                nextTriggerAt: null, applied: true, saved: true, reason: null, message: "已设置每 4 小时执行一次"
            });
            await flush();
            assert.strictEqual(button.disabled, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "已设置每 4 小时执行一次。下次时间暂不可用。");
        });

        it("keeps the input, shows the server reason and allows a retry after 503 saved=true (I-1/I-3)", async () => {
            let attempts = 0;
            const sandbox = scheduleSandbox({
                putImpl: () => {
                    attempts += 1;
                    if (attempts === 1) {
                        return Promise.reject(Object.assign(new Error("已保存但定时应用失败，请重试保存"), {
                            status: 503,
                            data: {
                                mode: "LEGACY", editable: true, source: "OVERRIDE", intervalHours: 6, anchorAt: null,
                                nextTriggerAt: null, applied: false, saved: true, reason: "APPLY_FAILED",
                                message: "已保存但定时应用失败，请重试保存"
                            }
                        }));
                    }
                    return {
                        mode: "LEGACY", editable: true, source: "OVERRIDE", intervalHours: 6, anchorAt: null,
                        nextTriggerAt: null, applied: true, saved: true, reason: null, message: "已设置每 6 小时执行一次"
                    };
                }
            });
            await openDiscoveryModal(sandbox);

            sandbox.$("#discoveryScheduleHours").value = "6";
            sandbox.$("#discoveryScheduleSave").click();
            await flush();

            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "已保存但定时应用失败，请重试保存");
            assert.strictEqual(sandbox.statuses.length, 0, "a failed save must never surface as a success toast");
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false, "the operator can retry");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "6", "the typed value is kept for the retry");

            sandbox.$("#discoveryScheduleSave").click();
            await flush();
            assert.strictEqual(attempts, 2);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "已设置每 6 小时执行一次。下次时间暂不可用。");
        });

        it("surfaces the server reason for a rejected 400 and keeps the value editable", async () => {
            const sandbox = scheduleSandbox({
                putError: Object.assign(new Error("执行间隔必须是 1～168 的整数小时"), {
                    status: 400,
                    data: { reason: "INVALID_INTERVAL_HOURS", message: "执行间隔必须是 1～168 的整数小时" }
                })
            });
            await openDiscoveryModal(sandbox);

            sandbox.$("#discoveryScheduleHours").value = "12";
            sandbox.$("#discoveryScheduleSave").click();
            await flush();

            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "执行间隔必须是 1～168 的整数小时");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "12");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").disabled, false);
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false);
            assert.strictEqual(sandbox.statuses.length, 0);
        });

        it("hides the hour controls and explains the mode when the server refuses with 409", async () => {
            const sandbox = scheduleSandbox({
                putError: Object.assign(new Error("当前为连续发现模式，小时周期设置不适用"), {
                    status: 409,
                    data: {
                        mode: "CONTINUOUS", editable: false, source: "CONFIG", intervalHours: null, anchorAt: null,
                        nextTriggerAt: null, applied: false, saved: false, reason: "CONTINUOUS_MODE",
                        message: "当前为连续发现模式，小时周期设置不适用"
                    }
                })
            });
            await openDiscoveryModal(sandbox);

            sandbox.$("#discoveryScheduleHours").value = "8";
            sandbox.$("#discoveryScheduleSave").click();
            await flush();

            assert.strictEqual(sandbox.$("#discoveryScheduleControls").hidden, true);
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, true);
            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "当前为连续发现模式，小时周期设置不适用");
            assert.strictEqual(sandbox.statuses.length, 0);
        });

        it("reports a body-less save failure and keeps the value for a retry", async () => {
            const sandbox = scheduleSandbox({ putError: new Error("请求超时（10 秒）") });
            await openDiscoveryModal(sandbox);

            sandbox.$("#discoveryScheduleHours").value = "9";
            sandbox.$("#discoveryScheduleSave").click();
            await flush();

            assert.strictEqual(sandbox.$("#discoveryScheduleHint").textContent, "保存失败：请求超时（10 秒），请重试保存");
            assert.strictEqual(sandbox.$("#discoveryScheduleHours").value, "9");
            assert.strictEqual(sandbox.$("#discoveryScheduleSave").disabled, false);
        });
    });
});
