const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const source = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = source.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

const sandbox = { currentTaskModal: null };
vm.createContext(sandbox);
vm.runInContext(extractFn("escapeHtml"), sandbox);
vm.runInContext(extractFn("badge"), sandbox);
vm.runInContext(extractFn("renderBatchTable"), sandbox);
vm.runInContext(extractFn("renderRunRow"), sandbox);
vm.runInContext(extractFn("formatDateTime"), sandbox);

const runtimePath = path.join(__dirname, "..", "..", "main", "resources", "static", "task-modal-runtime.js");
const runtimeSource = fs.readFileSync(runtimePath, "utf-8");
vm.runInContext(runtimeSource, sandbox);

const renderBatchTable = sandbox.renderBatchTable;
const renderRunRow = sandbox.renderRunRow;
const isProgressTerminal = sandbox.isProgressTerminal;
const isExecutionTerminal = sandbox.isExecutionTerminal;
const isCurrentTaskModal = sandbox.isCurrentTaskModal;

const { describe, it } = require("node:test");

describe("renderBatchTable (from app.js)", () => {

    it("empty array returns placeholder", () => {
        const html = renderBatchTable([]);
        assert.ok(html.includes("暂无批次日志"), "should show empty placeholder");
    });

    it("null returns placeholder", () => {
        const html = renderBatchTable(null);
        assert.ok(html.includes("暂无批次日志"), "should show empty placeholder");
    });

    it("renders all rows for normal logs", () => {
        const logs = [
            { batchNumber: 1, batchProcessed: 10, batchPassed: 8, batchRejected: 2, processedCount: 10, totalCount: 100, createdAt: "2026-06-10T10:00:00" },
            { batchNumber: 2, batchProcessed: 10, batchPassed: 9, batchRejected: 1, processedCount: 20, totalCount: 100, createdAt: "2026-06-10T10:01:00" }
        ];
        const html = renderBatchTable(logs);
        assert.ok(html.includes("<td>1</td>"), "batchNumber 1 present");
        assert.ok(html.includes("<td>2</td>"), "batchNumber 2 present");
        assert.ok(html.includes("10/100"), "cumulative progress present");
        assert.ok(html.includes("20/100"), "cumulative progress present");
        assert.ok(html.includes("10%"), "percentage present");
        assert.ok(html.includes("20%"), "percentage present");
    });

    it("batchNumber values rendered as-is (filtering done server-side)", () => {
        const logs = [
            { batchNumber: 1, batchProcessed: 5, batchPassed: 5, batchRejected: 0, processedCount: 5, totalCount: 10, createdAt: null }
        ];
        const html = renderBatchTable(logs);
        assert.ok(html.includes("<td>1</td>"), "batchNumber rendered");
        assert.ok(html.includes("<td>5</td>"), "batchProcessed rendered");
    });

    it("handles null createdAt", () => {
        const logs = [
            { batchNumber: 1, batchProcessed: 0, batchPassed: 0, batchRejected: 0, processedCount: 0, totalCount: 0, createdAt: null }
        ];
        const html = renderBatchTable(logs);
        assert.ok(html.includes(">-</td>"), "null createdAt falls back to dash");
    });
});

describe("renderRunRow (from app.js)", () => {

    it("renders RUNNING status correctly", () => {
        const run = {
            executionId: 1, taskType: "EXPERT_REVALIDATION", triggerType: "MANUAL",
            status: "RUNNING", startedAt: "2026-06-10 10:00:00", finishedAt: null,
            durationSeconds: null, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(html.includes("运行中"), "RUNNING status label present");
        assert.ok(html.includes("手动"), "MANUAL trigger label present");
        assert.ok(html.includes("▶"), "collapsed arrow present");
    });

    it("renders SUCCESS status correctly", () => {
        const run = {
            executionId: 2, taskType: "EXPERT_REVALIDATION", triggerType: "SCHEDULED",
            status: "SUCCESS", startedAt: "2026-06-10 10:00:00", finishedAt: "2026-06-10 10:05:00",
            durationSeconds: 300, totalProcessed: 100, totalPassed: 80, totalRejected: 20
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(html.includes("成功"), "SUCCESS status label present");
        assert.ok(html.includes("定时"), "SCHEDULED trigger label present");
        assert.ok(html.includes("300秒"), "duration rendered");
        assert.ok(html.includes("100"), "totalProcessed rendered");
        assert.ok(html.includes("80"), "totalPassed rendered");
        assert.ok(html.includes("20"), "totalRejected rendered");
    });

    it("renders CANCELLED status correctly", () => {
        const run = {
            executionId: 3, taskType: "RAW_PROMOTION_SCAN", triggerType: "MANUAL",
            status: "CANCELLED", startedAt: "2026-06-10 10:00:00", finishedAt: "2026-06-10 10:02:00",
            durationSeconds: 120, totalProcessed: 50, totalPassed: 30, totalRejected: 20
        };
        const html = renderRunRow(run, "RAW_PROMOTION_SCAN");
        assert.ok(html.includes("已取消"), "CANCELLED status label present");
    });

    it("renders FAILED status correctly", () => {
        const run = {
            executionId: 4, taskType: "EXPERT_DISCOVERY", triggerType: "MANUAL",
            status: "FAILED", startedAt: "2026-06-10 10:00:00", finishedAt: "2026-06-10 10:00:30",
            durationSeconds: 30, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_DISCOVERY");
        assert.ok(html.includes("失败"), "FAILED status label present");
        assert.ok(html.includes("30秒"), "duration rendered");
    });

    it("null durationSeconds renders dash", () => {
        const run = {
            executionId: 5, taskType: "EXPERT_REVALIDATION", triggerType: "MANUAL",
            status: "RUNNING", startedAt: "2026-06-10 10:00:00", finishedAt: null,
            durationSeconds: null, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(html.includes("<td>-</td>"), "null duration renders dash");
    });

    it("XSS-safe: triggerType with script tags escaped", () => {
        const run = {
            executionId: 6, taskType: "EXPERT_DISCOVERY", triggerType: '<img src=x onerror=alert(1)>',
            status: "SUCCESS", startedAt: "2026-06-10 10:00:00", finishedAt: "2026-06-10 10:05:00",
            durationSeconds: 300, totalProcessed: 100, totalPassed: 80, totalRejected: 20
        };
        const html = renderRunRow(run, "EXPERT_DISCOVERY");
        assert.ok(!html.includes("<img"), "raw img tag in triggerType must be escaped");
        assert.ok(html.includes("&lt;img"), "triggerType should contain escaped img");
    });

    it("unknown status renders as-is (safe fallback)", () => {
        const run = {
            executionId: 20, taskType: "EXPERT_REVALIDATION", triggerType: "MANUAL",
            status: "UNKNOWN_STATUS_XYZ", startedAt: "2026-06-10 10:00:00", finishedAt: null,
            durationSeconds: null, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(html.includes("UNKNOWN_STATUS_XYZ"), "unknown status label preserved via escapeHtml in badge");
    });

    it("escapes XSS in startedAt", () => {
        const run = {
            executionId: 7, taskType: "EXPERT_REVALIDATION", triggerType: "MANUAL",
            status: "RUNNING", startedAt: '<img src=x onerror=alert(1)>', finishedAt: null,
            durationSeconds: null, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(!html.includes("<img"), "raw <img tag must be escaped");
        assert.ok(html.includes("&lt;img"), "should contain escaped img");
    });

    it("expanded row shows down arrow", () => {
        sandbox.currentTaskModal = { expandedExecutionId: 8 };
        const run = {
            executionId: 8, taskType: "EXPERT_REVALIDATION", triggerType: "MANUAL",
            status: "RUNNING", startedAt: "2026-06-10 10:00:00", finishedAt: null,
            durationSeconds: null, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(html.includes("▼"), "expanded row shows down arrow");
        sandbox.currentTaskModal = null;
    });

    it("data attributes set correctly", () => {
        const run = {
            executionId: 42, taskType: "EXPERT_REVALIDATION", triggerType: "MANUAL",
            status: "SUCCESS", startedAt: "2026-06-10 10:00:00", finishedAt: null,
            durationSeconds: null, totalProcessed: 0, totalPassed: 0, totalRejected: 0
        };
        const html = renderRunRow(run, "EXPERT_REVALIDATION");
        assert.ok(html.includes('data-execution-id="42"'), "data-execution-id present");
        assert.ok(html.includes('data-status="SUCCESS"'), "data-status present");
        assert.ok(html.includes("toggleRunDetail"), "onclick handler present");
    });

    it("CANCELLING status renders as 取消中", () => {
        const run = {
            executionId: 30, taskType: "RAW_PROMOTION_SCAN", triggerType: "MANUAL",
            status: "CANCELLING", startedAt: "2026-06-10 10:00:00", finishedAt: null,
            durationSeconds: null, totalProcessed: 50, totalPassed: 30, totalRejected: 20
        };
        const html = renderRunRow(run, "RAW_PROMOTION_SCAN");
        assert.ok(html.includes("取消中"), "CANCELLING status label present");
        assert.ok(html.includes('data-status="CANCELLING"'), "CANCELLING data-status");
    });

    it("empty execution list shows placeholder", () => {
        // renderRunList behavior: empty list → placeholder text
        sandbox.currentTaskModal = {
            taskType: "EXPERT_REVALIDATION", generation: 1,
            batchLogsByExecutionId: {}, runStatusByExecutionId: {}
        };
        // Simulate: no runs = empty html rendered by renderRunList
        // We can't call renderRunList directly (not extracted), but we verify
        // the placeholder pattern is used in the app.js source
        assert.ok(source.includes("暂无执行记录"), "source contains empty execution placeholder");
        sandbox.currentTaskModal = null;
    });
});

describe("state machine helpers (from app.js)", () => {

    it("isProgressTerminal recognizes terminal states", () => {
        assert.strictEqual(isProgressTerminal("COMPLETED"), true);
        assert.strictEqual(isProgressTerminal("FAILED"), true);
        assert.strictEqual(isProgressTerminal("CANCELLED"), true);
        assert.strictEqual(isProgressTerminal("RUNNING"), false);
        assert.strictEqual(isProgressTerminal("CANCELLING"), false);
    });

    it("isExecutionTerminal recognizes non-active states", () => {
        assert.strictEqual(isExecutionTerminal("RUNNING"), false);
        assert.strictEqual(isExecutionTerminal("CANCELLING"), false);
        assert.strictEqual(isExecutionTerminal("SUCCESS"), true);
        assert.strictEqual(isExecutionTerminal("FAILED"), true);
        assert.strictEqual(isExecutionTerminal("CANCELLED"), true);
        assert.strictEqual(isExecutionTerminal("PARTIAL_SUCCESS"), true);
    });

    it("isCurrentTaskModal matches taskType and generation", () => {
        sandbox.currentTaskModal = { taskType: "EXPERT_REVALIDATION", generation: 5 };
        assert.strictEqual(isCurrentTaskModal("EXPERT_REVALIDATION", 5), true);
        assert.strictEqual(isCurrentTaskModal("EXPERT_REVALIDATION", 4), false);
        assert.strictEqual(isCurrentTaskModal("RAW_PROMOTION_SCAN", 5), false);
        sandbox.currentTaskModal = null;
    });

    it("isCurrentTaskModal returns false when modal is null", () => {
        sandbox.currentTaskModal = null;
        assert.strictEqual(isCurrentTaskModal("EXPERT_REVALIDATION", 1), false);
    });

    it("isCurrentTaskModal handles undefined generation", () => {
        sandbox.currentTaskModal = { taskType: "EXPERT_REVALIDATION", generation: undefined };
        assert.strictEqual(isCurrentTaskModal("EXPERT_REVALIDATION", undefined), true);
        sandbox.currentTaskModal = null;
    });
});
