const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function element(extra = {}) {
    return Object.assign({
        hidden: false,
        textContent: "",
        innerHTML: "",
        className: "",
        disabled: false,
        style: {}
    }, extra);
}

// Shared DOM stub for the batch log drawer family of functions.
function drawerElements() {
    return {
        batchExecutionLogDrawer: element({ hidden: true }),
        batchLogDrawerTitle: element(),
        batchLogExecutionSelect: element({ hidden: false }),
        batchLogLive: element({ hidden: true }),
        batchLogLiveStatus: element(),
        batchLogLiveRound: element(),
        batchLogLiveFill: element({ style: {} }),
        batchLogLiveCounts: element(),
        batchLogLiveMessage: element(),
        batchLogLiveAccounts: element(),
        batchLogLiveCancelBtn: element({ hidden: false }),
        batchLogMetrics: element(),
        batchManualConfirmOkBtn: element({ disabled: false })
    };
}

function escapeHtmlStub(value) {
    return String(value == null ? "" : value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

describe("batch manual execution log drawer", () => {
    it("confirmManualExecution without source calls openBatchExecutionLogs (I-6)", async () => {
        const elements = drawerElements();
        const apiBodies = [];
        const calls = { openExecution: [], openConfig: [] };
        const state = { manualSource: null, logConfigId: null, logExecutionId: null, logMode: null, logRefreshTimer: null };
        const sandbox = {
            batchTaskState: state,
            document: { getElementById: (id) => elements[id] || null },
            api: async (url, options) => {
                apiBodies.push(JSON.parse(options.body));
                return { executionId: 101 };
            },
            closeBatchManualConfirmDialog: () => {},
            showStatus: () => {},
            openBatchExecutionLogs: (executionId) => { calls.openExecution.push(executionId); },
            openBatchConfigLogs: (configId, executionId) => { calls.openConfig.push({ configId, executionId }); },
            readManualFormValues: () => ({
                mailType: "INTRODUCTION", roundsPerRun: 1, roundSize: 2,
                perMailIntervalMs: 3000, perRoundIntervalMs: 60000,
                selfCheckTtlMinutes: 30, funnelLevel: "CANDIDATE",
                tags: [], emailDomain: null, discipline: null, templateId: 1
            })
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("confirmManualExecution"), sandbox);
        vm.runInContext(extractFn("buildManualExecutionSnapshot"), sandbox);

        await sandbox.confirmManualExecution();

        assert.strictEqual(apiBodies.length, 1);
        assert.strictEqual(apiBodies[0].sourceConfigId, null, "independent run must POST null sourceConfigId");
        assert.deepStrictEqual(calls.openExecution, [101], "independent run must open execution logs");
        assert.deepStrictEqual(calls.openConfig, [], "config logs must not open without a source");
    });

    it("confirmManualExecution with source still calls openBatchConfigLogs(source.id) (I-6 regression)", async () => {
        const elements = drawerElements();
        const apiBodies = [];
        const calls = { openExecution: [], openConfig: [] };
        const state = { manualSource: { id: 7 }, logConfigId: null, logExecutionId: null, logMode: null, logRefreshTimer: null };
        const sandbox = {
            batchTaskState: state,
            document: { getElementById: (id) => elements[id] || null },
            api: async (url, options) => {
                apiBodies.push(JSON.parse(options.body));
                return { executionId: 202 };
            },
            closeBatchManualConfirmDialog: () => {},
            showStatus: () => {},
            openBatchExecutionLogs: (executionId) => { calls.openExecution.push(executionId); },
            openBatchConfigLogs: (configId, executionId) => { calls.openConfig.push({ configId, executionId }); },
            readManualFormValues: () => ({
                mailType: "INTRODUCTION", roundsPerRun: 1, roundSize: 2,
                perMailIntervalMs: 3000, perRoundIntervalMs: 60000,
                selfCheckTtlMinutes: 30, funnelLevel: "CANDIDATE",
                tags: [], emailDomain: null, discipline: null, templateId: 1
            })
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("confirmManualExecution"), sandbox);
        vm.runInContext(extractFn("buildManualExecutionSnapshot"), sandbox);

        await sandbox.confirmManualExecution();

        assert.strictEqual(apiBodies.length, 1);
        assert.strictEqual(apiBodies[0].sourceConfigId, 7, "config-sourced run must POST its sourceConfigId");
        assert.deepStrictEqual(calls.openConfig, [{ configId: 7, executionId: 202 }], "must keep config log routing");
        assert.deepStrictEqual(calls.openExecution, []);
    });

    it("openBatchExecutionLogs writes identity before requesting and shapes the drawer (I-4/S-2)", async () => {
        const elements = drawerElements();
        const stateAtCall = [];
        const resolvers = [];
        const state = { logConfigId: null, logExecutionId: null, logMode: null, logRefreshTimer: null };
        const sandbox = {
            batchTaskState: state,
            document: { getElementById: (id) => elements[id] || null },
            api: async (url) => {
                stateAtCall.push({
                    url,
                    logConfigId: state.logConfigId,
                    logExecutionId: state.logExecutionId,
                    logMode: state.logMode
                });
                return new Promise((resolve) => { resolvers.push(resolve); });
            },
            clearBatchLogRefreshTimer: () => { state.logRefreshTimer = null; },
            showStatus: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("openBatchExecutionLogs"), sandbox);
        vm.runInContext(extractFn("loadBatchLogDetail"), sandbox);

        sandbox.openBatchExecutionLogs(101);

        assert.strictEqual(stateAtCall.length, 1);
        assert.strictEqual(stateAtCall[0].url, "/api/mail/batch-send/executions/101");
        assert.strictEqual(stateAtCall[0].logConfigId, null);
        assert.strictEqual(stateAtCall[0].logExecutionId, 101, "identity must be written before api()");
        assert.strictEqual(stateAtCall[0].logMode, "execution");
        assert.strictEqual(elements.batchExecutionLogDrawer.hidden, false);
        assert.strictEqual(elements.batchLogDrawerTitle.textContent, "执行日志（独立执行）");
        assert.strictEqual(elements.batchLogExecutionSelect.hidden, true, "independent runs hide the execution select");
        assert.strictEqual(state.logExecutionId, 101);
    });

    it("openBatchExecutionLogs with no executionId degrades with a status message", () => {
        const elements = drawerElements();
        const statuses = [];
        const sandbox = {
            batchTaskState: { logConfigId: null, logExecutionId: null, logMode: null, logRefreshTimer: null },
            document: { getElementById: (id) => elements[id] || null },
            loadBatchLogDetail: () => { throw new Error("must not load"); },
            showStatus: (message, type) => { statuses.push({ message, type }); }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("openBatchExecutionLogs"), sandbox);

        sandbox.openBatchExecutionLogs(null);

        assert.strictEqual(statuses.length, 1);
        assert.match(statuses[0].message, /未能定位到日志/);
        assert.strictEqual(elements.batchExecutionLogDrawer.hidden, true, "drawer must stay closed");
    });

    it("loadBatchLogDetail uses the executions route for configId null (I-3 routing)", async () => {
        const elements = drawerElements();
        const urls = [];
        const sandbox = {
            batchTaskState: { logConfigId: null, logExecutionId: 101, logMode: "execution", logRefreshTimer: null },
            document: { getElementById: (id) => elements[id] || null },
            api: async (url) => {
                urls.push(url);
                return { status: "SUCCESS", executionId: 101 };
            },
            renderBatchExecutionDetail: () => {},
            clearBatchLogRefreshTimer: () => { sandbox.batchTaskState.logRefreshTimer = null; },
            setInterval: () => 1,
            clearInterval: () => {},
            console
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("loadBatchLogDetail"), sandbox);

        await sandbox.loadBatchLogDetail(null, 101);

        assert.deepStrictEqual(urls, ["/api/mail/batch-send/executions/101"]);
        assert.strictEqual(elements.batchLogLive.hidden, true, "final state must hide the live block");
    });

    it("loadBatchLogDetail keeps the config route when configId is set", async () => {
        const elements = drawerElements();
        const urls = [];
        const sandbox = {
            batchTaskState: { logConfigId: 7, logExecutionId: 101, logMode: "config", logRefreshTimer: null },
            document: { getElementById: (id) => elements[id] || null },
            api: async (url) => {
                urls.push(url);
                return { status: "SUCCESS", executionId: 101 };
            },
            renderBatchExecutionDetail: () => {},
            clearBatchLogRefreshTimer: () => { sandbox.batchTaskState.logRefreshTimer = null; },
            setInterval: () => 1,
            clearInterval: () => {},
            console
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("loadBatchLogDetail"), sandbox);

        await sandbox.loadBatchLogDetail(7, 101);

        assert.deepStrictEqual(urls, ["/api/mail/batch-send/configs/7/executions/101"]);
    });

    it("late response from an earlier open does not overwrite the current render (I-4)", async () => {
        const elements = drawerElements();
        const resolvers = [];
        const rendered = [];
        const state = { logConfigId: null, logExecutionId: null, logMode: null, logRefreshTimer: null };
        const sandbox = {
            batchTaskState: state,
            document: { getElementById: (id) => elements[id] || null },
            api: async () => new Promise((resolve) => { resolvers.push(resolve); }),
            clearBatchLogRefreshTimer: () => { state.logRefreshTimer = null; },
            renderBatchExecutionDetail: (detail) => { rendered.push(detail.executionId); },
            setInterval: () => 1,
            clearInterval: () => {},
            console
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("openBatchExecutionLogs"), sandbox);
        vm.runInContext(extractFn("loadBatchLogDetail"), sandbox);

        sandbox.openBatchExecutionLogs(101);
        sandbox.openBatchExecutionLogs(202);
        assert.strictEqual(resolvers.length, 2, "two pending detail requests");

        // Resolve B first, then A (stale)
        resolvers[1]({ status: "SUCCESS", executionId: 202 });
        await new Promise((r) => setImmediate(r));
        resolvers[0]({ status: "SUCCESS", executionId: 101 });
        await new Promise((r) => setImmediate(r));

        assert.deepStrictEqual(rendered, [202], "stale A response must be dropped");
        assert.strictEqual(state.logExecutionId, 202);
    });

    it("renderBatchLiveSection hides the block when live is null", () => {
        const elements = drawerElements();
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            escapeHtml: escapeHtmlStub
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderBatchLiveSection"), sandbox);

        sandbox.renderBatchLiveSection({ status: "SUCCESS", live: null });

        assert.strictEqual(elements.batchLogLive.hidden, true);
    });

    it("renderBatchLiveSection renders counts with estimated total and no fake percent (I-5)", () => {
        const elements = drawerElements();
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            escapeHtml: escapeHtmlStub
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderBatchLiveSection"), sandbox);

        sandbox.renderBatchLiveSection({
            live: {
                status: "RUNNING", message: "正在发送：someone@example.edu", roundNumber: 1,
                processedCount: 7, totalCount: 120, percentage: 5,
                accounts: [{ accountCode: "a@weibo.com", success: 3, failed: 0 }],
                cancellable: true
            }
        });

        assert.strictEqual(elements.batchLogLive.hidden, false);
        assert.strictEqual(elements.batchLogLiveStatus.className, "badge ok");
        assert.strictEqual(elements.batchLogLiveStatus.textContent, "运行中");
        assert.strictEqual(elements.batchLogLiveRound.textContent, "第 1 轮");
        assert.strictEqual(elements.batchLogLiveCounts.textContent, "已处理 7 / 约 120（5%）");
        assert.strictEqual(elements.batchLogLiveFill.style.width, "5%");
        assert.strictEqual(elements.batchLogLiveMessage.textContent, "正在发送：someone@example.edu");
        assert.match(elements.batchLogLiveAccounts.innerHTML, /a@weibo\.com 成功 3 \/ 失败 0/);
        assert.strictEqual(elements.batchLogLiveCancelBtn.hidden, false);
    });

    it("renderBatchLiveSection with totalCount 0 shows no percent (I-5)", () => {
        const elements = drawerElements();
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            escapeHtml: escapeHtmlStub
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderBatchLiveSection"), sandbox);

        sandbox.renderBatchLiveSection({
            live: {
                status: "RUNNING", message: null, roundNumber: 1,
                processedCount: 7, totalCount: 0, percentage: 0,
                accounts: [], cancellable: true
            }
        });

        assert.strictEqual(elements.batchLogLiveCounts.textContent, "已处理 7");
        assert.ok(!elements.batchLogLiveCounts.textContent.includes("%"),
            "no percentage may be shown when totalCount is 0");
        assert.strictEqual(elements.batchLogLiveFill.style.width, "0%");
    });

    it("renderBatchLiveSection renders raw message and escapes accountCode", () => {
        const elements = drawerElements();
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            escapeHtml: escapeHtmlStub
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderBatchLiveSection"), sandbox);

        sandbox.renderBatchLiveSection({
            live: {
                status: "RUNNING", message: "正在发送：<b>x</b>", roundNumber: 1,
                processedCount: 1, totalCount: 2, percentage: 50,
                accounts: [{ accountCode: "<script>alert(1)</script>", success: 1, failed: 1 }],
                cancellable: true
            }
        });

        assert.strictEqual(elements.batchLogLiveMessage.textContent, "正在发送：<b>x</b>");
        assert.ok(!elements.batchLogLiveAccounts.innerHTML.includes("<script>"),
            "accountCode must be escaped before innerHTML");
        assert.match(elements.batchLogLiveAccounts.innerHTML, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
        assert.match(elements.batchLogLiveAccounts.innerHTML, /is-failing/, "failed > 0 must mark the chip");
    });

    it("renderBatchLiveSection hides the cancel button when not cancellable", () => {
        const elements = drawerElements();
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            escapeHtml: escapeHtmlStub
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderBatchLiveSection"), sandbox);

        sandbox.renderBatchLiveSection({
            live: {
                status: "CANCELLING", message: null, roundNumber: 1,
                processedCount: 1, totalCount: 2, percentage: 50,
                accounts: [], cancellable: false
            }
        });

        assert.strictEqual(elements.batchLogLiveCancelBtn.hidden, true);
        assert.strictEqual(elements.batchLogLiveStatus.className, "badge warn");
        assert.strictEqual(elements.batchLogLiveStatus.textContent, "取消中");
    });

    it("handleBatchLiveCancel posts to the executions cancel route only (I-2)", async () => {
        const elements = drawerElements();
        const apiCalls = [];
        const statuses = [];
        const detailLoads = [];
        const state = { logConfigId: null, logExecutionId: 101, logMode: "execution", logRefreshTimer: null };
        const sandbox = {
            batchTaskState: state,
            document: { getElementById: (id) => elements[id] || null },
            api: async (url, options) => {
                apiCalls.push({ url, method: options && options.method });
                return { message: "已发送取消请求，将在当前批次结束后停止" };
            },
            confirm: () => true,
            showStatus: (message, type) => { statuses.push({ message, type }); },
            loadBatchLogDetail: (configId, executionId) => { detailLoads.push({ configId, executionId }); }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("handleBatchLiveCancel"), sandbox);

        await sandbox.handleBatchLiveCancel();

        assert.strictEqual(apiCalls.length, 1);
        assert.match(apiCalls[0].url, /^\/api\/mail\/batch-send\/executions\/101\/cancel$/);
        assert.strictEqual(apiCalls[0].method, "POST");
        assert.strictEqual(statuses[0].message, "已发送取消请求，将在当前批次结束后停止");
        assert.strictEqual(statuses[0].type, "ok");
        assert.deepStrictEqual(detailLoads, [{ configId: null, executionId: 101 }]);
        assert.strictEqual(elements.batchLogLiveCancelBtn.disabled, false, "button must be re-enabled in finally");

        const source = extractFn("handleBatchLiveCancel");
        assert.ok(!source.includes("task-progress"), "cancel must never hit the taskType-level route");
    });

    it("handleBatchLiveCancel declines when the user cancels the confirm", async () => {
        const elements = drawerElements();
        const apiCalls = [];
        const sandbox = {
            batchTaskState: { logConfigId: null, logExecutionId: 101, logMode: "execution", logRefreshTimer: null },
            document: { getElementById: (id) => elements[id] || null },
            api: async (url, options) => { apiCalls.push(url); return {}; },
            confirm: () => false,
            showStatus: () => {},
            loadBatchLogDetail: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("handleBatchLiveCancel"), sandbox);

        await sandbox.handleBatchLiveCancel();

        assert.deepStrictEqual(apiCalls, [], "no request when the operator declines");
    });

    it("closeBatchLogDrawer hides the live block and cleans the timer (I-7)", () => {
        const elements = drawerElements();
        const cleared = [];
        const state = {
            logConfigId: 7,
            logExecutionId: 101,
            logMode: "config",
            logRefreshTimer: { id: 1 }
        };
        const sandbox = {
            batchTaskState: state,
            document: { getElementById: (id) => elements[id] || null },
            clearInterval: (timer) => { cleared.push(timer); }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("closeBatchLogDrawer"), sandbox);
        vm.runInContext(extractFn("clearBatchLogRefreshTimer"), sandbox);

        sandbox.closeBatchLogDrawer();

        assert.strictEqual(elements.batchExecutionLogDrawer.hidden, true);
        assert.strictEqual(elements.batchLogLive.hidden, true, "live block must be hidden");
        assert.strictEqual(state.logMode, null);
        assert.strictEqual(state.logConfigId, null);
        assert.strictEqual(state.logExecutionId, null);
        assert.strictEqual(state.logRefreshTimer, null, "timer handle must be released");
        assert.deepStrictEqual(cleared, [{ id: 1 }]);
    });

    it("only one setInterval handle exists for the log drawer (I-7)", () => {
        const drawerFns = [
            "openBatchConfigLogs",
            "openBatchExecutionLogs",
            "loadBatchLogDetail",
            "loadBatchLogExecutions",
            "closeBatchLogDrawer"
        ];
        for (const name of drawerFns) {
            const source = extractFn(name);
            const lines = source.split("\n").filter((line) => line.includes("setInterval"));
            for (const line of lines) {
                assert.match(line, /batchTaskState\.logRefreshTimer\s*=\s*setInterval/,
                    name + " must assign setInterval only to batchTaskState.logRefreshTimer");
            }
        }
    });

    it("renderBatchLiveSection writes no inline styles except the fill width (S-1)", () => {
        const source = extractFn("renderBatchLiveSection");
        assert.ok(!source.includes("style=\""), "no inline style attributes in generated HTML");
        const styleWrites = source.match(/[a-zA-Z_$][\w$]*\.style\.[a-zA-Z]+/g) || [];
        assert.deepStrictEqual(styleWrites, ["fill.style.width"], "only the fill width may be set via style");
    });
});
