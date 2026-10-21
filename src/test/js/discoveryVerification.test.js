const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { test } = require("node:test");
const root = path.join(__dirname, "../../main/resources/static");
const app = fs.readFileSync(path.join(root, "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf8");
const section = app.slice(app.indexOf("// apollo-verification-ui:start"), app.indexOf("// apollo-verification-ui:end") + "// apollo-verification-ui:end".length);
const extractFn = name => app.match(new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}"))[0];
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
const counts = { profiles: 123, autoAdmitted: 55, directAdmitted: 15, apolloAdmitted: 40, clear: 36, unclear: 19, pending: 45, errors: 5, queued: 18, protected: 0, projectionPending: 0, projectionErrors: 1, apolloEntered: 108, apolloSearched: 85, apolloCandidates: 66, apolloMatched: 40, apolloHttpAttempts: 108, registrationErrors: 0 };
const F123 = { executionId: 3001, coverage: "RECORDED", counts, lifecycle: "ACTIVE", snapshotVersion: 17, asOf: "2026-10-18T10:00:00Z", mode: "ENFORCE", collectionStatus: "SUCCESS", suggested: null };
const run = { executionId: 3001, status: "SUCCESS", triggerType: "MANUAL", startedAt: "2026-10-18 18:00:00", durationSeconds: 5, totalProcessed: 9999, totalPassed: 9999 };
function node() {
    const classes = new Set();
    const children = new Map();
    return { innerHTML: "", textContent: "", hidden: false, checked: false, disabled: false, value: "", dataset: {}, listeners: {},
        classList: { add: x => classes.add(x), remove: x => classes.delete(x), contains: x => classes.has(x) },
        closest() { return this; }, addEventListener(t, f) { this.listeners[t] = f; }, removeEventListener(t) { delete this.listeners[t]; },
        querySelector(s) { if (!children.has(s)) children.set(s, node()); return children.get(s); }, querySelectorAll() { return []; },
        setAttribute(k, v) { this[k] = v; }, focus() { this.focused = true; }, contains() { return false; }, remove() {}, insertAdjacentHTML(_where, value) { this.innerHTML += value; }
    };
}
function sandbox(handler = async () => []) {
    const elements = new Map();
    // Only real static IDs are admitted. Dynamic IDs are explicitly installed by each view test.
    for (const id of ["taskModalRunBody", "taskModalRunHead", "discoveryVerificationLogHost", "taskLaunchApolloRow", "taskLaunchApolloAssist", "taskLaunchApolloHint"]) {
        assert.ok(html.includes('id="' + id + '"'), id + " must exist in actual index.html");
        elements.set(id, node());
    }
    elements.get("taskModalRunHead").innerHTML = '<tr><th id="taskModalTrafficHead">下载量</th><th>总处理</th></tr>';
    const timers = [], calls = [], views = [];
    const context = { console, URLSearchParams, AbortController, Map, Set,
        currentTaskModal: { taskType: "EXPERT_DISCOVERY", generation: 1, batchLogsByExecutionId: {}, runStatusByExecutionId: {} },
        document: { activeElement: null, getElementById: id => elements.get(id) || null },
        $: selector => elements.get(selector.slice(1)) || null,
        isCurrentTaskModal(type, generation) { return this.currentTaskModal?.taskType === type && this.currentTaskModal?.generation === generation; },
        formatBeijingInstant: value => String(value), formatFileSize: value => value + " B", showStatus: () => {},
        setTimeout: (fn, ms) => { const t = { fn, ms }; timers.push(t); return t; }, clearTimeout: t => { if (t) t.cancelled = true; },
        api: async (url, options) => { calls.push({ url, options }); return handler(url, options); }
    };
    // Function is called as a global, not a method in the VM.
    context.isCurrentTaskModal = (type, generation) => context.currentTaskModal?.taskType === type && context.currentTaskModal?.generation === generation;
    vm.createContext(context);
    vm.runInContext(extractFn("escapeHtml") + "\n" + section + "\n;globalThis.state = () => apvState;", context);
    context.apvInit("EXPERT_DISCOVERY", 1);
    context.elements = elements; context.calls = calls; context.timers = timers; context.views = views;
    return context;
}
function isolateViews(s) {
    s.apvRenderRuns = () => s.views.push(s.state()?.summaries.get(s.state()?.executionId));
    s.apvRenderDetail = () => {};
    s.apvRenderRecords = () => {};
}
function seed(s) { s.state().runs = [run]; s.state().executionId = 3001; s.currentTaskModal.expandedExecutionId = 3001; }
function page(version = 17) { return { coverage: "RECORDED", items: [], runCounts: counts, totalBatches: 3, snapshotVersion: version }; }
function evidenceView(s) {
    seed(s);
    s.state().tab = "records";
    s.state().summaries.set(3001, F123);
    s.state().records = { total: 1, items: [{ itemId: 1, name: "Fixture", route: "APOLLO", resultLabel: "APOLLO_UNCLEAR" }] };
    const panel = node();
    s.elements.set("apv-records-3001", panel);
    return panel.querySelector("tbody");
}
function evidenceClick(s) {
    const button = { dataset: { action: "toggle-evidence", itemId: "1" }, closest() { return this; } };
    s.apvEvent({ type: "click", target: button, stopPropagation() {} });
}
function cancelHandler(s, pipelineState) {
    s.confirm = () => true;
    s.PIPELINE_LAUNCH_TIMEOUT_MS = 15000;
    s.currentTaskModal.pipelineMode = pipelineState != null;
    s.currentTaskModal.pipelineState = pipelineState;
    s.elements.set("taskModalCancelBtn", node());
    vm.runInContext(extractFn("handleCancelTask"), s);
}

test("S1/S2 real IDs, static head and original controls", () => {
    for (const id of ["taskModalTrafficHead", "taskLaunchKeywordInput", "taskLaunchSources", "taskLaunchAdvancedRow", "taskLaunchRunBtn", "discoveryReviewTab", "discoveryReviewHistoryTab"]) assert.ok(html.includes('id="' + id + '"'));
    const option = html.slice(html.indexOf('<div class="apv-option"'), html.indexOf('id="taskLaunchFiltersRow"'));
    assert.doesNotMatch(option, /style=|onclick=/);
    assert.match(css, /\.apv-option input\[type="checkbox"\] \{\n    width: 14px;\n    height: 14px;/);
    assert.match(css, /\.apv-logs \.data-table th \{\n    position: static;\n    background: #f8fafc;/);
});

test("I4 all 16 existing resource keys share the new key and runtime stays unversioned", () => {
    const values = [...html.matchAll(/\?v=([^"<>]+)/g)].map(m => m[1]);
    assert.equal(values.length, 16); assert.equal(new Set(values).size, 1);
    assert.match(html, /<script src="task-modal-runtime\.js"><\/script>/);
});

test("I1 F123 uses ledger counts, never original processed/passed", () => {
    const s = sandbox(); seed(s);
    const row = s.apvRunHtml(run, F123);
    assert.equal((row.match(/<td\b/g) || []).length, 11);
    assert.match(row, /<td>123<\/td><td class="apv-number">55<\/td><td>40<\/td>/);
    assert.match(row, /核验中/); assert.doesNotMatch(row, /9999/);
    const summary = s.apvSummaryHtml(F123);
    for (const value of ["待核验 45", "错误 5", "排队/核验中 18", "进入辅助核验 108", "已查询 85", "返回候选 66", "满足匹配规则 40", "同步错误 1"]) assert.ok(summary.includes(value), value);
});

test("I1 null, real zero, audit and missing statistics are distinct", () => {
    const s = sandbox(); seed(s);
    assert.match(s.apvRunHtml(run, { ...F123, coverage: "NOT_RECORDED", counts: null }), /未记录/);
    assert.match(s.apvRunHtml(run, { ...F123, counts: { ...counts, profiles: 0, autoAdmitted: 0 } }), /<td>0<\/td><td class="apv-number">0<\/td>/);
    assert.match(s.apvSummaryHtml({ ...F123, mode: "AUDIT", counts: { ...counts, autoAdmitted: 0 }, suggested: { autoAdmitted: 55, directAdmitted: 15, apolloAdmitted: 40 } }), /影子核验建议：建议准入 55/);
    s.state().error = "503 unavailable";
    assert.match(s.apvRunHtml(run, null), /读取失败/);
    assert.match(s.apvSummaryHtml({ ...F123, coverage: "NOT_RECORDED", counts: null }), /data-action="filter-direct" disabled/);
});

test("I3 S3–S8 target DOM, columns, controls and markup have no inline presentation", () => {
    const s = sandbox(); seed(s);
    const markup = s.apvDetailHtml(3001);
    assert.match(markup, /colspan="11"/); assert.match(markup, /colspan="13"/); assert.match(markup, /colspan="8"/); assert.match(markup, /colspan="7"/);
    for (const tab of ["batches", "records", "collection"]) assert.match(markup, new RegExp(`aria-controls="apv-${tab}-3001"`));
    assert.equal((markup.match(/role="tabpanel"/g) || []).length, 3);
    assert.doesNotMatch(markup, /style=|onclick=|max-height|ellipsis/);
    assert.doesNotMatch(markup.match(/<details class="apv-traffic"[^>]*>/)[0], /open/);
});

test("S5 batch renderer uses full run footer, server cumulative time and all five reasons", () => {
    const s = sandbox(); seed(s);
    const panel = node(); s.elements.set("apv-batches-3001", panel);
    s.state().batches = { ...page(), runCounts: counts, items: [{ batchNumber: 2, counts: { ...counts, profiles: 40 }, cumulativeProfiles: 80, updatedAt: F123.asOf, reasons: Array.from({ length: 5 }, (_, i) => ({ code: "CODE-" + i, label: "原因" + i, count: i + 1 })) }] };
    s.apvRenderBatches();
    const body = panel.querySelector("tbody").innerHTML;
    assert.match(body, /<td>80<\/td>/); assert.match(body, /CODE-4/); assert.match(body, /原因4 · 5 人/);
    assert.match(panel.querySelector("tfoot").innerHTML, /<td>123<\/td>/);
    assert.equal(panel.querySelector('[data-action="batch-prev"]').disabled, true);
    assert.equal(panel.querySelector('[data-action="batch-next"]').disabled, true);
});

test("S7 evidence escapes external text, rejects unsafe links and shows typed Apollo stages", () => {
    const s = sandbox();
    const record = { itemId: 9, name: '<img onerror="alert(1)">', resultLabel: "APOLLO_UNCLEAR", projectionState: "ERROR" };
    const evidence = { field: "identity", value: "<script>", provider: "ORCID", sourceUrl: "javascript:alert(1)", temporalScope: "HISTORICAL", fetchedAt: F123.asOf };
    const output = s.apvEvidenceHtml({ qualification: "UNKNOWN", evidence: [evidence], attempts: [], reasons: [{ code: "LONG-CODE", label: "完整原因", count: 1 }], apolloPersonId: "person-fixture", apolloStages: { enteredAt: F123.asOf, searchedAt: F123.asOf, candidateFoundAt: F123.asOf, matchedAt: F123.asOf } }, record);
    assert.equal((output.match(/class="apv-evidence-item"/g) || []).length, 4);
    assert.match(output, /&lt;img/); assert.doesNotMatch(output, /<img|href="javascript:/);
    for (const text of ["person-fixture", "进入辅助", "已查询", "返回候选", "满足匹配规则", "未取得可靠证据", "历史", "采集日期", "LONG-CODE"]) assert.ok(output.includes(text));
    assert.match(s.apvSourceHtml({ ...evidence, sourceUrl: 'https://example.invalid/?x="a"' }), /rel="noopener noreferrer"/);
});

test("S5 collection reasons and traffic preserve complete source/host lists", () => {
    const s = sandbox(); seed(s);
    s.state().runs = [{ ...run, traffic: { totalBytes: 5, metadataBytes: 2, fulltextBytes: 3, oversizedDownloads: 1, oversizedBytes: 2, discardedBytes: 1, bySource: { A: 1, B: 2 }, byHost: { one: 1, two: 1, three: 1, four: 1, five: 1 } } }];
    const reasons = Object.fromEntries(Array.from({ length: 5 }, (_, i) => ['<CODE-' + i + '>', i]));
    const collection = s.apvCollectionHtml([{ batchNumber: 1, batchRejectReasonsJson: JSON.stringify(reasons) }]);
    assert.match(collection, /&lt;CODE-4&gt;/); assert.doesNotMatch(collection, /style=|ellipsis|<CODE/);
    assert.match(s.apvTrafficHtml(3001), /five 1 B/); assert.match(s.apvTrafficHtml(3001), /Apollo\/ORCID/);
});

test("I2 four-part identity and Abort isolate old task, generation, execution and filter", async () => {
    let resolve;
    const s = sandbox(() => new Promise(r => { resolve = r; })); seed(s);
    const token = s.apvToken();
    const pending = s.apvGet("/read", token);
    s.apvSuspend();
    assert.equal(s.calls[0].options.signal.aborted, true);
    resolve({ old: true }); assert.equal(await pending, null);
    for (const change of [{ taskType: "OTHER" }, { generation: 2 }, { executionId: 2 }, { filterSequence: 99 }]) assert.equal(s.apvIsCurrent({ ...s.apvToken(), ...change }), false);
});

test("I2 collection SUCCESS keeps 5s verification polling and stops on ledger terminal", async () => {
    let terminal = false;
    const s = sandbox(async url => url.includes("/summary") ? [{ ...F123, lifecycle: terminal ? "COMPLETED" : "ACTIVE" }] : page());
    seed(s); isolateViews(s);
    await s.apvRefresh();
    const timer = s.timers.at(-1); assert.equal(timer.ms, 5000);
    terminal = true; await timer.fn(); await flush();
    assert.equal(s.state().timer, null);
    s.apvDispose(); const calls = s.calls.length;
    await timer.fn(); assert.equal(s.calls.length, calls);
});

test("F07-01 collapse during GET then reopen requests and renders fresh evidence", async () => {
    const pending = [];
    const s = sandbox(() => new Promise(resolve => pending.push(resolve)));
    const body = evidenceView(s);
    evidenceClick(s);
    assert.equal(s.calls.length, 1);
    assert.match(body.innerHTML, /正在读取依据/);
    evidenceClick(s);
    pending[0]({ executionId: 3001, snapshotVersion: 17, apolloPersonId: "discarded" });
    await flush();
    assert.equal(s.state().evidence.has(1), false);
    evidenceClick(s);
    assert.equal(s.calls.length, 2);
    pending[1]({ executionId: 3001, snapshotVersion: 17, apolloPersonId: "fresh-evidence" });
    await flush();
    assert.match(body.innerHTML, /fresh-evidence/);
    assert.doesNotMatch(body.innerHTML, /正在读取依据|discarded/);
    evidenceClick(s); evidenceClick(s);
    assert.equal(s.calls.length, 2, "successful evidence remains cached");
});

test("F07-01 reopen before old GET finishes cannot let its late response overwrite the new read", async () => {
    const pending = [];
    const s = sandbox(() => new Promise(resolve => pending.push(resolve)));
    const body = evidenceView(s);
    evidenceClick(s); evidenceClick(s); evidenceClick(s);
    assert.equal(s.calls.length, 2);
    pending[1]({ executionId: 3001, snapshotVersion: 17, apolloPersonId: "current-person" });
    await flush();
    pending[0]({ executionId: 3001, snapshotVersion: 17, apolloPersonId: "old-person" });
    await flush();
    assert.equal(s.state().evidence.get(1).apolloPersonId, "current-person");
    assert.match(body.innerHTML, /current-person/);
    assert.doesNotMatch(body.innerHTML, /old-person/);
});

test("F07-01 suspended evidence is aborted and cannot overwrite the reopened current filter", async () => {
    const pending = [];
    const s = sandbox(() => new Promise(resolve => pending.push(resolve)));
    const body = evidenceView(s);
    evidenceClick(s);
    s.apvSetVisible(false);
    assert.equal(s.calls[0].options.signal.aborted, true);
    assert.equal(s.state().evidence.has(1), false);
    s.state().visible = true;
    s.state().route = "APOLLO"; s.state().result = "UNCLEAR"; s.state().q = "Fixture";
    const current = s.apvLoadEvidence(1);
    pending[1]({ executionId: 3001, snapshotVersion: 17, apolloPersonId: "current-filter" });
    await current;
    pending[0]({ executionId: 3001, snapshotVersion: 17, apolloPersonId: "hidden-filter" });
    await flush();
    assert.match(body.innerHTML, /current-filter/);
    assert.doesNotMatch(body.innerHTML, /hidden-filter/);
    assert.equal(s.state().route, "APOLLO");
    assert.equal(s.state().result, "UNCLEAR");
    assert.equal(s.state().q, "Fixture");
});

for (const [operation, pipelineState, endpoint] of [
    ["pause", "RUNNING", "/api/task-progress/EXPERT_DISCOVERY/cancel"],
    ["resume", "PAUSED", "/api/expert-discovery/pipeline/resume"],
    ["cancel", null, "/api/task-progress/EXPERT_DISCOVERY/cancel"]
]) {
    test(`F07-02 ${operation} restarts ACTIVE verification despite unchanged collection, then stops at terminal`, async () => {
        let finishOperation, terminal = false;
        const s = sandbox(async (url, options) => {
            if (options?.method === "POST") return new Promise(resolve => { finishOperation = resolve; });
            return url.includes("/summary") ? [{ ...F123, lifecycle: terminal ? "COMPLETED" : "ACTIVE" }] : page();
        });
        seed(s); isolateViews(s); cancelHandler(s, pipelineState);
        s.state().route = "APOLLO"; s.state().result = "PENDING"; s.state().recordPage = 2;
        await s.apvRefresh();
        const originalTimer = s.state().timer;
        const operationRequest = s.handleCancelTask();
        assert.equal(originalTimer.cancelled, true);
        assert.equal(s.state().timer, null);
        assert.equal(s.calls.find(call => call.options?.method === "POST").url, endpoint);
        finishOperation({});
        await operationRequest; await flush();
        const refreshed = s.calls.filter(call => call.url.includes("/summary")).length;
        assert.equal(refreshed, 2);
        assert.equal(s.state().timer.ms, 5000);
        s.apvReceiveRuns([{ ...run }], "EXPERT_DISCOVERY", 1);
        await flush();
        assert.equal(s.calls.filter(call => call.url.includes("/summary")).length, refreshed);
        await s.state().timer.fn(); await flush();
        assert.equal(s.calls.filter(call => call.url.includes("/summary")).length, refreshed + 1);
        assert.equal(s.state().executionId, 3001);
        assert.equal(s.state().route, "APOLLO");
        assert.equal(s.state().result, "PENDING");
        assert.equal(s.state().recordPage, 2);
        terminal = true;
        await s.state().timer.fn(); await flush();
        assert.equal(s.state().timer, null);
        const calls = s.calls.length;
        s.apvReceiveRuns([{ ...run }], "EXPERT_DISCOVERY", 1);
        await flush();
        assert.equal(s.calls.length, calls);
    });
}

test("F07-02 failed collection operation also restores current verification polling", async () => {
    const s = sandbox(async (url, options) => {
        if (options?.method === "POST") throw Error("operation unavailable");
        return url.includes("/summary") ? [F123] : page();
    });
    seed(s); isolateViews(s); cancelHandler(s, "RUNNING");
    await s.apvRefresh();
    await s.handleCancelTask(); await flush();
    assert.equal(s.calls.filter(call => call.url.includes("/summary")).length, 2);
    assert.equal(s.state().timer.ms, 5000);
});

for (const transition of ["closed", "other task", "new generation", "main review tab"]) {
    test(`F07-02 operation finishing after ${transition} leaves verification stopped`, async () => {
        let finishOperation;
        const s = sandbox(async (url, options) => {
            if (options?.method === "POST") return new Promise(resolve => { finishOperation = resolve; });
            return url.includes("/summary") ? [F123] : page();
        });
        seed(s); isolateViews(s); cancelHandler(s, "RUNNING");
        await s.apvRefresh();
        const operation = s.handleCancelTask();
        if (transition === "closed") { s.apvDispose(); s.currentTaskModal = null; }
        else if (transition === "other task") { s.apvDispose(); s.currentTaskModal.taskType = "EXPERT_REVALIDATION"; }
        else if (transition === "new generation") { s.apvInit("EXPERT_DISCOVERY", 2); s.currentTaskModal.generation = 2; }
        else s.apvSetVisible(false);
        const calls = s.calls.length;
        finishOperation({});
        await operation; await flush();
        assert.equal(s.calls.length, calls);
        assert.equal(s.state()?.timer ?? null, null);
    });
}

test("I2 unchanged terminal collection lists do not restart verification polling", async () => {
    const s = sandbox(async url => url.includes("/summary") ? [F123] : page());
    seed(s); isolateViews(s);
    s.state().summaries.set(3001, { ...F123, lifecycle: "COMPLETED" });
    s.apvReceiveRuns([{ ...run }], "EXPERT_DISCOVERY", 1);
    await flush();
    assert.equal(s.calls.length, 0);
    s.apvReceiveRuns([{ ...run }, { ...run, executionId: 3005 }], "EXPERT_DISCOVERY", 1);
    await flush();
    assert.ok(s.calls.some(call => call.url.includes("/summary")));
});

test("I2 snapshot mismatch never mixes totals with a different current page", async () => {
    const s = sandbox(async url => url.includes("/summary") ? [F123] : page(18)); seed(s); isolateViews(s);
    s.state().summaries.set(3001, { ...F123, snapshotVersion: 16 });
    await s.apvRefresh();
    assert.equal(s.state().summaries.get(3001).snapshotVersion, 16);
    assert.equal(s.state().batches, null); assert.match(s.state().error, /快照已变化/);
});

test("I1 503 retains last successful asOf and only GET reload is available", async () => {
    const s = sandbox(async () => { const e = Error("unavailable"); e.status = 503; throw e; }); seed(s); isolateViews(s);
    s.state().summaries.set(3001, F123); s.state().batches = page();
    await s.apvRefresh();
    assert.equal(s.state().summaries.get(3001), F123); assert.equal(s.state().batches.snapshotVersion, 17);
    assert.match(s.apvAlert(s.state().error, F123.asOf), /503.*2026-10-18/);
    assert.ok(s.calls.every(call => !call.options.method));
});

test("S8 collection GET recovery clears its alert without dropping the successful cache", async () => {
    let unavailable = true;
    const s = sandbox(async () => { if (unavailable) throw Error("collection unavailable"); return [{ batchNumber: 1, batchProcessed: 1 }]; });
    seed(s);
    const panel = node();
    let alert = null;
    panel.querySelector = () => alert;
    panel.insertAdjacentHTML = (_where, value) => { alert = { html: value, remove() { alert = null; } }; };
    s.elements.set("apv-collection-3001", panel);
    s.elements.set("batch-body-3001", node());
    await s.apvLoadCollection(s.apvToken());
    assert.match(alert.html, /collection unavailable/);
    unavailable = false;
    await s.apvLoadCollection(s.apvToken());
    assert.equal(alert, null);
    assert.equal(s.currentTaskModal.batchLogsByExecutionId[3001][0].batchProcessed, 1);
    assert.ok(s.calls.every(call => !call.options.method));
});

test("I2 old A response cannot alter B or closed/reopened modal", async () => {
    const resolvers = [];
    const s = sandbox(() => new Promise(resolve => resolvers.push(resolve))); seed(s); isolateViews(s);
    const pending = s.apvRefresh();
    s.apvSuspend(); s.state().executionId = 3002;
    resolvers.forEach(resolve => resolve([F123])); await pending;
    assert.equal(s.state().summaries.size, 0); assert.equal(s.views.length, 0);
    s.apvDispose(); assert.equal(s.state(), null);
    assert.match(s.elements.get("taskModalRunHead").innerHTML, /taskModalTrafficHead/);
    assert.equal(s.elements.get("discoveryVerificationLogHost").classList.contains("apv-logs"), false);
});

test("S2 capabilities defaults are applied once and failure is explicit", async () => {
    const s = sandbox(async () => ({ enabled: true, ready: true, apolloDefault: true, reasonCodes: [] }));
    await s.apvLoadCapabilities(1);
    assert.equal(s.elements.get("taskLaunchApolloAssist").checked, true);
    assert.equal(s.apvCanLaunch(true), true);
    s.elements.get("taskLaunchApolloAssist").checked = false;
    seed(s); isolateViews(s); s.api = async url => url.includes("summary") ? [F123] : page(); await s.apvRefresh();
    assert.equal(s.elements.get("taskLaunchApolloAssist").checked, false);
    const disabled = sandbox(async () => ({ enabled: false, ready: false, apolloDefault: true, reasonCodes: ["VERIFICATION_DISABLED"] }));
    await disabled.apvLoadCapabilities(1); assert.equal(disabled.elements.get("taskLaunchApolloAssist").disabled, true);
    assert.equal(disabled.elements.get("taskLaunchApolloHint").textContent, "资格核验未启用");
    const broken = sandbox(async () => { throw Error("503 offline"); });
    await assert.rejects(broken.apvLoadCapabilities(1), /503/); assert.equal(broken.apvCanLaunch(true), false);
});

test("S6 filter and server pagination requests use size20, reset pages, debounce300ms", () => {
    const s = sandbox(); seed(s); isolateViews(s); s.apvRefresh = () => {};
    s.state().recordPage = 5;
    s.apvEvent({ type: "change", target: { dataset: { filter: "route" }, value: "APOLLO" } });
    assert.equal(s.state().recordPage, 0); assert.match(s.apvRecordsPath(), /route=APOLLO/); assert.match(s.apvRecordsPath(), /size=20/);
    s.apvEvent({ type: "input", target: { dataset: { filter: "q" }, value: "new name" } });
    assert.equal(s.timers.at(-1).ms, 300);
    s.state().result = "UNCLEAR"; s.state().batchNumber = 3;
    assert.match(s.apvRecordsPath(), /result=UNCLEAR/); assert.match(s.apvRecordsPath(), /batchNumber=3/);
});

test("S4 keyboard tabs keep selected tabindex and cancel obsolete requests", () => {
    const s = sandbox(); seed(s); isolateViews(s); s.apvRefresh = () => {};
    for (const tab of ["batches", "records", "collection"]) s.elements.set(`apv-tab-${tab}-3001`, node());
    let prevented = false;
    s.apvEvent({ type: "keydown", key: "End", target: { closest: () => ({}) }, preventDefault: () => { prevented = true; } });
    assert.equal(s.state().tab, "collection"); assert.equal(prevented, true); assert.equal(s.elements.get("apv-tab-collection-3001").focused, true);
    s.apvSetVisible(false); assert.equal(s.state().visible, false);
});

test("S2 launch propagates frozen Apollo option in both real transport shapes", async () => {
    const calls = [];
    const s = { URLSearchParams, PIPELINE_LAUNCH_TIMEOUT_MS: 15000, api: async (url, options) => calls.push({ url, options }) };
    vm.createContext(s); vm.runInContext(extractFn("postDiscoveryLaunch"), s);
    await s.postDiscoveryLaunch("AI, ML", ["ORCID"], false, true, true);
    assert.match(calls[0].url, /apolloAssist=true/); assert.equal(calls[0].options.body, undefined);
    await s.postDiscoveryLaunch("", ["OPENALEX"], false, false, false);
    assert.deepEqual(JSON.parse(calls[1].options.body), { sources: ["OPENALEX"], apolloAssist: false });
    const body = extractFn("executeDiscover");
    assert.ok(body.indexOf('const apolloAssist') < body.indexOf('openTaskModal('));
    assert.match(body, /postDiscoveryLaunch\(keywords, selectedSources, includeRawScan, continuous, apolloAssist\)/);
});

test("matching V2 evidence shows the selected person and escapes provider text", () => {
    const s = sandbox();
    const output = s.apvEvidenceHtml({
        qualification: "UNKNOWN", matchingVersion: 2, apolloPersonId: "p1",
        matchedCandidate: { fullName: "Ada <Lee>", title: "Professor of Physics", institution: {name: "Example University"},
            headline: "<img src=x onerror=alert(1)>", employmentHistory: [{institution:{name:"Old University"}, title:"Researcher", current:false, startDate:"2010-01-01", endDate:"2020-01-01"}] },
        academicResearchFields: "Physics", attempts: [], reasons: []
    }, {name:"Ada Lee", itemId:1, resultLabel:"APOLLO_CLEAR", matchingLabel:"教授职称匹配"});
    assert.match(output, /匹配规则 V2/);
    assert.match(output, /教授职称匹配/);
    assert.match(output, /Old University/);
    assert.match(output, /Professor of Physics/);
    assert.doesNotMatch(output, /<img src=x/);
    assert.match(output, /&lt;img/);
});
