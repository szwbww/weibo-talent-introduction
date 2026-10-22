const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("node:assert/strict");
const { test } = require("node:test");

const staticDir = path.join(__dirname, "../../main/resources/static");
const source = fs.readFileSync(path.join(staticDir, "app.js"), "utf8");
const index = fs.readFileSync(path.join(staticDir, "index.html"), "utf8");
function extractFn(name) {
    const match = source.match(new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}"));
    assert.ok(match, name);
    return match[0];
}
function harness() {
    const elements = {};
    const ids = ["batchLogMetrics", "batchLogIntegrityWarning", "batchLogStatusInfo", "batchLogLive", "batchLogLiveStatus", "batchLogLiveRound", "batchLogLiveFill", "batchLogLiveCounts", "batchLogLiveMessage", "batchLogLiveAccounts", "batchLogLiveCancelBtn", "batchLogFailureSection", "batchLogSkippedSection", "batchLogErrorSamples", "batchLogFailureReasons", "batchLogSkippedReasons", "batchLogErrorSampleList", "batchLogTimeline"];
    ids.forEach(id => { elements[id] = { innerHTML: "", textContent: "", hidden: false, style: {} }; });
    const sandbox = {
        document: { getElementById: id => elements[id] || null },
        formatDuration: value => String(value), formatDateTime: value => String(value),
        batchTaskState: { logConfigId: null, logExecutionId: 99, logMode: "execution", logRefreshTimer: null },
        setInterval: () => 7,
        clearBatchLogRefreshTimer: () => { sandbox.batchTaskState.logRefreshTimer = null; },
        console
    };
    vm.createContext(sandbox);
    ["escapeHtml", "statusLabel", "triggerTypeLabel", "batchDemandExecution", "renderOutcomeMetrics", "renderIntegrityWarning", "renderLogStatusInfo", "renderBatchLiveSection", "renderReasons", "renderErrorSamples", "renderBatchTimeline", "renderBatchExecutionDetail", "loadBatchLogDetail"].forEach(name => vm.runInContext(extractFn(name), sandbox));
    return { sandbox, elements };
}
function fixture(overrides = {}) {
    return {
        status: "SUCCESS", target: 49, success: 30, failure: 2, skipped: 17, remaining: 0,
        durationMs: 123, triggerType: "MANUAL", finishedAt: "2026-10-22T12:00:00",
        execution: { version: 2, plannedSuccess: 30, remainingSuccessQuota: 0, scanned: 50, sourceExhausted: false },
        ...overrides
    };
}
function cards(html) {
    return [...html.matchAll(/<div class="batch-log-metric ([^"]*)"><div class="batch-log-metric-label">([^<]*)<\/div><div class="batch-log-metric-value">([^<]*)<\/div><\/div>/g)].map(match => ({ cls: match[1], label: match[2], value: match[3] }));
}
const labels = ["本次计划成功", "成功", "失败", "跳过", "实际处理", "未完成配额", "耗时"];

test("v2 renders seven ordered cards with the existing exact DOM and classes", () => {
    const { sandbox, elements } = harness();
    const d = fixture();
    sandbox.renderOutcomeMetrics(d);
    const html = elements.batchLogMetrics.innerHTML;
    const actual = cards(html);
    assert.deepEqual(actual.map(c => c.label), labels);
    assert.deepEqual(actual.map(c => c.value), ["30", "30", "2", "17", "49", "0", "123"]);
    assert.deepEqual(actual.map(c => c.cls), ["", "is-success", "is-failure", "is-skipped", "", "", ""]);
    assert.equal(html, actual.map(c => `<div class="batch-log-metric ${c.cls}"><div class="batch-log-metric-label">${c.label}</div><div class="batch-log-metric-value">${c.value}</div></div>`).join(""));
    assert.doesNotMatch(html, /style=/);
    sandbox.renderIntegrityWarning(d);
    assert.equal(elements.batchLogIntegrityWarning.hidden, true);
});

test("all dynamic metric strings are escaped", () => {
    const { sandbox, elements } = harness();
    sandbox.formatDuration = () => '<img src=x onerror="x">';
    sandbox.renderOutcomeMetrics(fixture({ target: "<b>49</b>" }));
    assert.match(elements.batchLogMetrics.innerHTML, /&lt;b&gt;49&lt;\/b&gt;/);
    assert.match(elements.batchLogMetrics.innerHTML, /&lt;img/);
    assert.doesNotMatch(elements.batchLogMetrics.innerHTML, /<img|<b>/);
});

test("historical 1757 retains v1 card order, text and integrity", () => {
    const { sandbox, elements } = harness();
    const d = fixture({ execution: null, target: 1757, success: 30, failure: 0, skipped: 17, remaining: 1710 });
    sandbox.renderOutcomeMetrics(d);
    assert.deepEqual(cards(elements.batchLogMetrics.innerHTML).map(c => [c.label, c.value]), [["目标", "1757"], ["成功", "30"], ["失败", "0"], ["跳过", "17"], ["耗时", "123"], ["剩余", "1710"]]);
    sandbox.renderIntegrityWarning(d);
    assert.equal(elements.batchLogIntegrityWarning.hidden, true);
    sandbox.renderIntegrityWarning({ ...d, target: 1758 });
    assert.equal(elements.batchLogIntegrityWarning.textContent, "统计待核对：目标 1758，但成功+失败+跳过+剩余=1757");
    sandbox.renderIntegrityWarning({ ...d, status: "RUNNING", target: 0 });
    assert.equal(elements.batchLogIntegrityWarning.hidden, true);
});

test("unknown and malformed execution fall back without discarding outcome", () => {
    const { sandbox, elements } = harness();
    const valid = fixture().execution;
    const missingScanned = { ...valid }; delete missingScanned.scanned;
    const invalid = [null, {}, "bad JSON", { ...valid, version: 3 }, { ...valid, version: "2" }, missingScanned, { ...valid, sourceExhausted: 1 }];
    for (const key of ["plannedSuccess", "remainingSuccessQuota", "scanned"]) {
        for (const value of [-1, 1.5, "30", null, Infinity, Number.MAX_SAFE_INTEGER + 1]) invalid.push({ ...valid, [key]: value });
    }
    for (const execution of invalid) {
        const d = fixture({ execution });
        sandbox.renderOutcomeMetrics(d);
        assert.equal(cards(elements.batchLogMetrics.innerHTML)[0].label, "目标");
        assert.equal(cards(elements.batchLogMetrics.innerHTML)[0].value, "49");
        sandbox.renderIntegrityWarning(d);
        assert.equal(elements.batchLogIntegrityWarning.hidden, true);
        sandbox.renderLogStatusInfo(d);
        assert.doesNotMatch(elements.batchLogStatusInfo.textContent, /候选已耗尽/);
    }
});

test("v2 checks both processed and quota equations even while running", () => {
    const { sandbox, elements } = harness();
    for (const status of ["RUNNING", "CANCELLING", "SUCCESS"]) {
        const valid = fixture({ status });
        for (const d of [fixture({ status, target: 50 }), fixture({ status, remaining: 1 }), fixture({ status, execution: { ...valid.execution, remainingSuccessQuota: 1 } })]) {
            sandbox.renderIntegrityWarning(d);
            assert.equal(elements.batchLogIntegrityWarning.hidden, false);
            assert.equal(elements.batchLogIntegrityWarning.textContent, "统计待核对：本次执行计数不一致");
        }
        sandbox.renderIntegrityWarning(valid);
        assert.equal(elements.batchLogIntegrityWarning.hidden, true);
    }
});

test("running to final detail refresh preserves counters, reasons, timeline and cancellation behavior", async () => {
    const { sandbox, elements } = harness();
    const execution = { version: 2, plannedSuccess: 30, remainingSuccessQuota: 10, scanned: 50, sourceExhausted: true };
    const common = fixture({ execution, target: 25, success: 20, skipped: 3,
        failureReasons: { TIMEOUT: { label: "超时", count: 2 } },
        skippedReasons: { FILTER: { label: "筛选", count: 3 } }, errorSamples: ["timeout sample"],
        progressRows: [{ kind: "ROUND", batchNumber: 1, status: "RUNNING", message: "进度25", processedCount: 25, totalCount: 0, batchProcessed: 25, createdAt: "2026-10-22T12:00:00", errors: [] }]
    });
    let response = { ...common, status: "RUNNING", finishedAt: null, live: { status: "RUNNING", roundNumber: 1, totalCount: 0, processedCount: 25, cancellable: true, accounts: [{ accountCode: "sender<1>", success: 20, failed: 2 }] } };
    const urls = [];
    sandbox.api = async url => { urls.push(url); return response; };
    await sandbox.loadBatchLogDetail(null, 99);
    const initialMetrics = elements.batchLogMetrics.innerHTML;
    assert.deepEqual(cards(initialMetrics).map(c => c.value), ["30", "20", "2", "3", "25", "10", "123"]);
    assert.equal(elements.batchLogIntegrityWarning.hidden, true);
    assert.match(elements.batchLogStatusInfo.textContent, /候选已耗尽$/);
    assert.equal(elements.batchLogLive.hidden, false);
    assert.equal(elements.batchLogLiveCancelBtn.hidden, false);
    assert.match(elements.batchLogLiveAccounts.innerHTML, /sender&lt;1&gt;/);
    assert.match(elements.batchLogFailureReasons.innerHTML, /超时/);
    assert.match(elements.batchLogSkippedReasons.innerHTML, /筛选/);
    assert.match(elements.batchLogErrorSampleList.innerHTML, /timeout sample/);
    assert.match(elements.batchLogTimeline.innerHTML, /进度25/);
    assert.equal(sandbox.batchTaskState.logRefreshTimer, 7);
    response = { ...common, live: null };
    await sandbox.loadBatchLogDetail(null, 99);
    assert.equal(elements.batchLogMetrics.innerHTML, initialMetrics);
    assert.equal(elements.batchLogLive.hidden, true);
    assert.equal(sandbox.batchTaskState.logRefreshTimer, null);
    assert.equal(elements.batchLogFailureSection.hidden, false);
    assert.equal(elements.batchLogSkippedSection.hidden, false);
    assert.equal(elements.batchLogErrorSamples.hidden, false);
    assert.match(elements.batchLogTimeline.innerHTML, /进度25/);
    assert.match(elements.batchLogStatusInfo.textContent, /候选已耗尽$/);
    assert.deepEqual(urls, ["/api/mail/batch-send/executions/99", "/api/mail/batch-send/executions/99"]);
});

test("real index owns the reused nodes and all sixteen resource keys agree", () => {
    assert.match(index, /id="batchLogMetrics" class="batch-log-metrics"/);
    assert.match(index, /id="batchLogIntegrityWarning" class="batch-log-integrity-warning" hidden/);
    assert.match(index, /id="batchLogStatusInfo"/);
    const keys = [...index.matchAll(/(?:src|href)="[^"?]+\?v=([^"&]+)"/g)].map(m => m[1]);
    assert.equal(keys.length, 16);
    assert.match(keys[0], /^[0-9]{8}-[a-z0-9-]+$/);
    assert.deepEqual([...new Set(keys)], [keys[0]]);
});
