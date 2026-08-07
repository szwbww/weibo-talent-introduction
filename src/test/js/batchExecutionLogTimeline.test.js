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

function createTimelineSandbox() {
    const elements = {};
    const sandbox = {
        document: {
            getElementById(id) {
                if (!elements[id]) elements[id] = { innerHTML: "", hidden: false, textContent: "" };
                return elements[id];
            }
        },
        escapeHtml(value) {
            return String(value == null ? "" : value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll('"', "&quot;")
                .replaceAll("'", "&#039;");
        },
        formatDateTime(dt) { return dt ? "T:" + dt : "—"; }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderBatchTimeline"), sandbox);
    vm.runInContext(extractFn("renderIntegrityWarning"), sandbox);
    vm.runInContext(extractFn("statusLabel"), sandbox);
    return { sandbox, elements };
}

function sampleRows() {
    return [
        { kind: "INIT", batchNumber: 0, status: "RUNNING", message: "正在初始化发送队列...", stopReason: null, processedCount: 0, totalCount: 10, batchProcessed: 0, errors: [], createdAt: "2026-08-06T10:00:01" },
        { kind: "ROUND", batchNumber: 1, status: "RUNNING", message: "第1轮完成", stopReason: null, processedCount: 5, totalCount: 10, batchProcessed: 5, errors: [], createdAt: "2026-08-06T10:00:31" },
        { kind: "FINAL", batchNumber: 0, status: "PAUSED", message: "批量发送已暂停：无可用邮箱账号，请检查并恢复账号。", stopReason: "NO_AVAILABLE_ACCOUNT", processedCount: 0, totalCount: 10, batchProcessed: 0, errors: ["发送失败 (a@b.com): TIMEOUT"], createdAt: "2026-08-06T10:01:00" }
    ];
}

describe("renderBatchTimeline (I-5 / S-1)", () => {
    it("reads createdAt only and never references updatedAt or startedAt", () => {
        const body = extractFn("renderBatchTimeline");
        assert.ok(!body.includes("updatedAt"), "function body must not read updatedAt");
        assert.ok(!body.includes("startedAt"), "function body must not read startedAt");
        assert.ok(!body.includes('style="'), "no inline styles allowed");
    });

    it("renders INIT/FINAL rows with phase label and is-phase class", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline(sampleRows());
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(html.includes('class="batch-timeline-row is-phase"'), "INIT/FINAL rows carry is-phase");
        assert.ok(html.includes('<span class="batch-timeline-phase">初始化</span>'));
        assert.ok(html.includes('<span class="batch-timeline-phase">结束</span>'));
    });

    it("renders ROUND rows with batch label", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline(sampleRows());
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(html.includes('<span class="batch-timeline-batch">批次 #1</span>'));
    });

    it("renders a concrete time from createdAt for every row", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline(sampleRows());
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(html.includes("T:2026-08-06T10:00:01"), "INIT time must render");
        assert.ok(html.includes("T:2026-08-06T10:00:31"), "ROUND time must render");
        assert.ok(html.includes("T:2026-08-06T10:01:00"), "FINAL time must render");
    });

    it("escapes message, stopReason and errors via escapeHtml", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline([
            { kind: "ROUND", batchNumber: 1, status: "RUNNING", message: '<b onmouseover="x">alert</b>', stopReason: "<x>", processedCount: 1, totalCount: 2, batchProcessed: 1, errors: ["a < b & c"], createdAt: "2026-08-06T10:00:31" }
        ]);
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(html.includes("&lt;b onmouseover=&quot;x&quot;&gt;alert&lt;/b&gt;"), "message escaped");
        assert.ok(html.includes("终止原因：&lt;x&gt;"), "stopReason escaped");
        assert.ok(html.includes("a &lt; b &amp; c"), "error sample escaped");
    });

    it("omits stop and error elements when absent", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline([
            { kind: "ROUND", batchNumber: 2, status: "RUNNING", message: "第2轮完成", stopReason: null, processedCount: 8, totalCount: 10, batchProcessed: 3, errors: [], createdAt: "2026-08-06T10:00:31" }
        ]);
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(!html.includes("batch-timeline-stop"), "no empty stop label");
        assert.ok(!html.includes("batch-timeline-errors"), "no empty errors block");
    });

    it("marks FAILED and CANCELLED rows as is-failed", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline([
            { kind: "FINAL", batchNumber: 0, status: "FAILED", message: "发送任务失败", stopReason: "SYSTEM_ERROR", processedCount: 0, totalCount: 1, batchProcessed: 0, errors: [], createdAt: "2026-08-06T10:01:00" },
            { kind: "FINAL", batchNumber: 0, status: "CANCELLED", message: "已取消", stopReason: null, processedCount: 0, totalCount: 1, batchProcessed: 0, errors: [], createdAt: "2026-08-06T10:02:00" }
        ]);
        const html = elements.batchLogTimeline.innerHTML;

        assert.strictEqual((html.match(/is-failed/g) || []).length, 2);
    });

    it("shows muted empty state without inline styles", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline([]);
        assert.ok(elements.batchLogTimeline.innerHTML.includes('class="muted">无执行过程记录</span>'));
    });

    it("omits status element for INIT rows while running (I-1)", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline([
            { kind: "INIT", batchNumber: 0, status: "RUNNING", message: "正在初始化发送队列...", stopReason: null, processedCount: 0, totalCount: 10, batchProcessed: 0, errors: [], createdAt: "2026-08-06T10:00:01" }
        ]);
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(!html.includes("batch-timeline-status"), "RUNNING rows must not emit status element");
        assert.ok(!html.includes("运行中"), "RUNNING rows must not read 运行中");
        assert.ok(html.includes('<span class="batch-timeline-phase">初始化</span>'), "phase label must remain");
        assert.ok(html.includes("正在初始化发送队列..."), "message must remain");
    });

    it("omits status element for ROUND rows while running (I-1)", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderBatchTimeline([
            { kind: "ROUND", batchNumber: 1, status: "RUNNING", message: "第1轮完成", stopReason: null, processedCount: 5, totalCount: 10, batchProcessed: 5, errors: [], createdAt: "2026-08-06T10:00:31" }
        ]);
        const html = elements.batchLogTimeline.innerHTML;

        assert.ok(!html.includes("batch-timeline-status"), "RUNNING rows must not emit status element");
        assert.ok(!html.includes("运行中"), "RUNNING rows must not read 运行中");
        assert.ok(html.includes('<span class="batch-timeline-batch">批次 #1</span>'), "batch label must remain");
        assert.ok(html.includes("第1轮完成"), "message must remain");
    });

    it("keeps status element and Chinese label for terminal statuses (I-1)", () => {
        const { sandbox, elements } = createTimelineSandbox();
        const terminal = [
            { status: "PAUSED", label: "已暂停" },
            { status: "SUCCESS", label: "已完成" },
            { status: "FAILED", label: "失败" },
            { status: "CANCELLED", label: "已取消" }
        ];
        terminal.forEach((t, i) => {
            sandbox.renderBatchTimeline([
                { kind: "FINAL", batchNumber: 0, status: t.status, message: "发送任务结束", stopReason: null, processedCount: 0, totalCount: 1, batchProcessed: 0, errors: [], createdAt: "2026-08-06T10:0" + i + ":00" }
            ]);
            const html = elements.batchLogTimeline.innerHTML;
            assert.ok(html.includes('<span class="batch-timeline-status">' + t.label + '</span>'), t.status + " must keep status element and label");
        });
    });

    it("never reads execution-level d.live or d.status (I-2)", () => {
        const body = extractFn("renderBatchTimeline");
        assert.ok(!body.includes("d.live"), "function body must not read d.live");
        assert.ok(!body.includes("d.status"), "function body must not read d.status");
    });
});

describe("renderIntegrityWarning (I-4)", () => {
    it("hides warning while running or cancelling", () => {
        const { sandbox, elements } = createTimelineSandbox();
        const d = { status: "RUNNING", target: 0, success: 3, failure: 0, skipped: 0, remaining: 0 };
        sandbox.renderIntegrityWarning(d);
        assert.strictEqual(elements.batchLogIntegrityWarning.hidden, true, "RUNNING must hide warning");

        d.status = "CANCELLING";
        sandbox.renderIntegrityWarning(d);
        assert.strictEqual(elements.batchLogIntegrityWarning.hidden, true, "CANCELLING must hide warning");
    });

    it("shows warning in terminal state when counts disagree", () => {
        const { sandbox, elements } = createTimelineSandbox();
        sandbox.renderIntegrityWarning({ status: "SUCCESS", target: 0, success: 3, failure: 0, skipped: 0, remaining: 0 });
        assert.strictEqual(elements.batchLogIntegrityWarning.hidden, false);
    });
});

describe("statusLabel (S-2)", () => {
    it("maps new statuses to Chinese labels", () => {
        const { sandbox } = createTimelineSandbox();
        assert.strictEqual(sandbox.statusLabel("PARTIAL_SUCCESS"), "部分成功");
        assert.strictEqual(sandbox.statusLabel("CANCELLING"), "取消中");
        assert.strictEqual(sandbox.statusLabel("PAUSED"), "已暂停");
        assert.strictEqual(sandbox.statusLabel("INTERRUPTED"), "已中断");
    });

    it("keeps existing mappings and fallbacks", () => {
        const { sandbox } = createTimelineSandbox();
        assert.strictEqual(sandbox.statusLabel("RUNNING"), "运行中");
        assert.strictEqual(sandbox.statusLabel("FAILED"), "失败");
        assert.strictEqual(sandbox.statusLabel("UNKNOWN_X"), "UNKNOWN_X");
        assert.strictEqual(sandbox.statusLabel(""), "—");
    });
});
