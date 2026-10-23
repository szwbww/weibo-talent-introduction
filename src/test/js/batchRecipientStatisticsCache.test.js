"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.join(__dirname, "../../main/resources/static");
const source = fs.readFileSync(path.join(root, "app.js"), "utf8");
const index = fs.readFileSync(path.join(root, "index.html"), "utf8");
function fn(name) {
    const match = source.match(new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}"));
    assert.ok(match, name);
    return match[0];
}
function fixture() {
    const nodes = new Map();
    const get = id => {
        if (!nodes.has(id)) {
            let html = "", text = "";
            nodes.set(id, { disabled: false, hidden: false,
                get innerHTML() { return html; }, set innerHTML(v) { html = v; text = v.replace(/<[^>]+>/g, ""); },
                get textContent() { return text; }, set textContent(v) { text = v; html = ""; }
            });
        }
        return nodes.get(id);
    };
    const timers = new Map(), calls = [], pending = [];
    let next = 0;
    const snapshots = {
        editor: { templateId: 3, mailType: "INTRODUCTION", roundSize: 30, gateFilterEnabled: true,
            regions: ["Europe"], expertTypes: ["ACADEMIC_RND"], emailVerificationEnabled: true,
            emailVerificationAllowedStates: ["deliverable"], excludeVerifiedUnavailableEmails: true },
        manual: { templateId: 3, mailType: "INTRODUCTION", roundSize: 30, gateFilterEnabled: true,
            regions: ["Europe"], expertTypes: ["ACADEMIC_RND"], emailVerificationEnabled: true,
            emailVerificationAllowedStates: ["deliverable"], excludeVerifiedUnavailableEmails: true }
    };
    const sandbox = {
        document: { getElementById: get }, batchGateState: { editor: {}, manual: {} },
        recipientPreviewRequestSeq: { editor: 0, manual: 0 }, recipientPreviewTimers: { editor: null, manual: null },
        setTimeout: (cb, ms) => { assert.equal(ms, 300); timers.set(++next, cb); return next; },
        clearTimeout: id => timers.delete(id),
        buildConfigEditorRecipientSnapshot: () => snapshots.editor,
        buildManualExecutionSnapshot: () => snapshots.manual,
        api: (url, opts) => { calls.push({ url, body: JSON.parse(opts.body) }); return new Promise((resolve, reject) => pending.push({ resolve, reject })); }
    };
    vm.createContext(sandbox);
    for (const name of ["escapeHtml", "recipientPreviewHintId", "setRecipientPreviewState", "resetRecipientPreview", "scheduleRecipientPreview", "renderRecipientStatistics", "refreshRecipientPreview"]) vm.runInContext(fn(name), sandbox);
    return { sandbox, get, snapshots, calls, pending,
        tick: () => { const jobs = [...timers.values()]; timers.clear(); jobs.forEach(cb => cb()); }, timers };
}
const flush = () => new Promise(resolve => setImmediate(resolve));
const ready = (count = 100, extra = {}) => Object.assign({ state: "READY", cacheKey: "same-key", stale: false,
    cachedAt: "2026-10-22T16:00:00", summary: { totalSendable: count, baseline: 110, excludedUnique: 10,
        eligibleBeforeLiveVerification: 100, awaitingVerification: 100, asOf: "2026-10-22T15:59:58", reasonHitsDetail: [] } }, extra);
const hint = (f, kind = "editor") => f.get(kind === "editor" ? "batchConfigEditorRecipientHint" : "batchManualRecipientHint");
const status = (f, kind = "editor") => f.get(kind === "editor" ? "batchConfigEditorRecipientStatus" : "batchManualRecipientStatus");
const button = (f, kind = "editor") => f.get(kind === "editor" ? "batchConfigEditorRecipientPreviewBtn" : "batchManualRecipientPreviewBtn");

test("ordinary opens and changes debounce to one cacheOnly POST and never recalculate MISS", async () => {
    const f = fixture();
    f.sandbox.resetRecipientPreview("editor");
    for (let i = 0; i < 4; i++) f.sandbox.scheduleRecipientPreview("editor");
    assert.equal(f.calls.length, 0);
    assert.equal(f.timers.size, 1);
    f.tick();
    assert.equal(f.calls.length, 1);
    assert.equal(f.calls[0].url, "/api/mail/batch-send/recipients/statistics?cacheOnly=true");
    assert.deepEqual(f.calls[0].body, f.snapshots.editor);
    assert.equal(button(f).disabled, false, "lookup is not a full statistics loading state");
    f.pending[0].resolve({ state: "MISS", cacheKey: "same-key", summary: null });
    await flush();
    assert.equal(status(f).textContent, "尚未统计");
    assert.equal(hint(f).textContent, "尚未统计，点击‘统计并缓存’获取参考人数。");
    assert.equal(f.calls.length, 1);
    assert.equal(f.timers.size, 0);
});

test("explicit refresh is one real-gate POST, disables duplicate clicks and restores on success/failure", async () => {
    const f = fixture();
    f.sandbox.scheduleRecipientPreview("editor");
    f.sandbox.refreshRecipientPreview("editor");
    f.sandbox.refreshRecipientPreview("editor");
    assert.equal(f.calls.length, 1);
    assert.equal(f.timers.size, 0);
    assert.equal(f.calls[0].url, "/api/mail/batch-send/recipients/statistics?refresh=true");
    assert.deepEqual(f.calls[0].body, f.snapshots.editor);
    assert.equal(button(f).disabled, true);
    assert.equal(button(f).textContent, "统计中…");
    f.pending[0].resolve(ready()); await flush();
    assert.equal(button(f).disabled, false);
    assert.equal(button(f).textContent, "刷新统计");
    f.sandbox.refreshRecipientPreview("editor");
    f.pending[1].reject(new Error("offline")); await flush();
    assert.equal(button(f).disabled, false);
    assert.equal(status(f).textContent, "刷新失败，保留上次缓存");
    assert.match(hint(f).innerHTML, /缓存目标估计：<strong>100<\/strong>/);
});

test("READY zero is distinct from MISS and repeated opens preserve both server timestamps", async () => {
    const f = fixture();
    for (let i = 0; i < 2; i++) {
        f.sandbox.resetRecipientPreview("editor"); f.sandbox.scheduleRecipientPreview("editor"); f.tick();
        f.pending[i].resolve(ready(0)); await flush();
        assert.equal(status(f).textContent, "已加载缓存");
        assert.match(hint(f).innerHTML, /缓存目标估计：<strong>0<\/strong>/);
        assert.match(hint(f).innerHTML, /缓存时间：<span>2026-10-22 16:00:00<\/span>/);
        assert.match(hint(f).innerHTML, /诊断采样时间：<span>2026-10-22 15:59:58<\/span>/);
    }
    assert.ok(f.calls.every(c => c.url.endsWith("cacheOnly=true")));
});

test("stale and REFRESHING show old values with explicit states without polling or scans", async () => {
    const f = fixture();
    f.sandbox.refreshRecipientPreview("editor", "cacheOnly");
    f.pending[0].resolve(ready(100, { stale: true })); await flush();
    assert.equal(status(f).textContent, "缓存已超过24小时");
    assert.match(hint(f).innerHTML, /缓存已超过24小时，请按需刷新。/);
    f.sandbox.refreshRecipientPreview("editor", "cacheOnly");
    f.pending[1].resolve(ready(100, { state: "REFRESHING" })); await flush();
    assert.equal(status(f).textContent, "正在刷新统计…");
    assert.equal(button(f).disabled, false);
    assert.match(hint(f).innerHTML, /2026-10-22 16:00:00/);
    assert.equal(f.calls.length, 2); assert.equal(f.timers.size, 0);
});

test("503 old summary uses matching key only and never rewrites cachedAt", async () => {
    const f = fixture();
    f.sandbox.refreshRecipientPreview("editor", "cacheOnly"); f.pending[0].resolve(ready()); await flush();
    f.sandbox.refreshRecipientPreview("editor");
    f.pending[1].reject({ status: 503, data: ready(100, { state: "ERROR", errorCode: "STATISTICS_UNAVAILABLE" }) }); await flush();
    assert.equal(status(f).textContent, "刷新失败，保留上次缓存");
    assert.match(hint(f).innerHTML, /缓存时间：<span>2026-10-22 16:00:00/);
    f.sandbox.refreshRecipientPreview("editor");
    f.pending[2].reject({ status: 503, data: ready(999, { state: "ERROR", cacheKey: "wrong-key" }) }); await flush();
    assert.doesNotMatch(hint(f).innerHTML, /999/);
    assert.doesNotMatch(hint(f).innerHTML, /缓存目标估计|2026-10-22 16:00:00/);
    assert.equal(status(f).textContent, "统计不可用");
    assert.equal(hint(f).textContent, "统计不可用，可重试。");
});

for (const kind of ["editor", "manual"]) test(kind + " discards cached statistics when unchanged form receives a new server key error", async () => {
    const f = fixture();
    const snapshot = JSON.stringify(f.snapshots[kind]);
    f.sandbox.refreshRecipientPreview(kind, "cacheOnly");
    f.pending[0].resolve(ready(100, { cacheKey: "A" })); await flush();
    assert.match(hint(f, kind).innerHTML, /缓存目标估计：<strong>100<\/strong>/);
    assert.match(hint(f, kind).innerHTML, /2026-10-22 16:00:00/);
    f.sandbox.refreshRecipientPreview(kind);
    f.pending[1].reject({ status: 503, data: {
        state: "ERROR", cacheKey: "B", summary: null, cachedAt: null,
        errorCode: "STATISTICS_UNAVAILABLE"
    } }); await flush();
    assert.equal(JSON.stringify(f.snapshots[kind]), snapshot, "template fingerprint changes without a form change");
    assert.doesNotMatch(hint(f, kind).innerHTML, /100|2026-10-22 16:00:00/);
    assert.equal(status(f, kind).textContent, "统计不可用");
    assert.equal(hint(f, kind).textContent, "统计不可用，可重试。");
    assert.equal(button(f, kind).disabled, false);
    assert.equal(button(f, kind).textContent, "统计并缓存");
    assert.equal(f.calls.length, 2, "failure does not trigger another statistics request");
    assert.ok(f.calls[0].url.endsWith("cacheOnly=true"));
    assert.ok(f.calls[1].url.endsWith("refresh=true"));
    assert.equal(hint(f, kind).recipientStatistics.envelope.cacheKey, "B");
    assert.equal(hint(f, kind).recipientStatistics.envelope.summary, null);
    assert.equal(hint(f, kind).recipientStatistics.envelope.cachedAt, null);
});

for (const late of ["resolve", "reject"]) test("A → B discards late A " + late + " and clears old count before lookup", async () => {
    const f = fixture();
    f.sandbox.refreshRecipientPreview("editor", "cacheOnly");
    f.snapshots.editor = { ...f.snapshots.editor, regions: ["Asia"] };
    f.sandbox.scheduleRecipientPreview("editor");
    assert.doesNotMatch(hint(f).innerHTML, /100/);
    f.tick(); f.pending[1].resolve({ state: "MISS", cacheKey: "B" }); await flush();
    const before = hint(f).textContent;
    f.pending[0][late](late === "resolve" ? ready() : { status: 503, data: ready(100, { state: "ERROR" }) }); await flush();
    assert.equal(hint(f).textContent, before); assert.equal(status(f).textContent, "尚未统计");
});

test("snapshot identity guards responses even before a change listener runs", async () => {
    const f = fixture(); f.sandbox.refreshRecipientPreview("editor", "cacheOnly");
    f.snapshots.editor.gateFilterEnabled = false;
    f.pending[0].resolve(ready()); await flush();
    assert.doesNotMatch(hint(f).innerHTML, /缓存目标估计/);
});

test("panels have independent sequences and reset/close invalidates pending work and timers", async () => {
    const f = fixture();
    f.sandbox.refreshRecipientPreview("editor", "cacheOnly"); f.sandbox.refreshRecipientPreview("manual", "cacheOnly");
    f.sandbox.resetRecipientPreview("editor");
    f.pending[0].resolve(ready(999)); f.pending[1].resolve(ready(100)); await flush();
    assert.doesNotMatch(hint(f).innerHTML, /999/); assert.match(hint(f, "manual").innerHTML, /100/);
    f.sandbox.scheduleRecipientPreview("editor"); f.sandbox.resetRecipientPreview("editor"); f.tick();
    assert.equal(f.calls.length, 2);
    for (const name of ["resetBatchTaskState", "hideBatchConfigEditor", "switchBatchSendTab"]) assert.match(fn(name), /resetRecipientPreview\(/);
    assert.match(fn("closeBatchSendTaskModal"), /resetBatchTaskState\(\)/);
});

test("first error envelope can bind its request key; empty ERROR/REFRESHING stay text-only", async () => {
    const f = fixture(); f.sandbox.refreshRecipientPreview("editor", "cacheOnly");
    f.pending[0].reject({ status: 503, data: ready(100, { state: "ERROR" }) }); await flush();
    assert.equal(status(f).textContent, "刷新失败，保留上次缓存");
    for (const state of ["ERROR", "REFRESHING"]) {
        f.sandbox.resetRecipientPreview("editor"); f.sandbox.refreshRecipientPreview("editor", "cacheOnly");
        f.pending.at(-1).resolve({ state, cacheKey: "empty" }); await flush();
        assert.equal(hint(f).innerHTML, "");
        assert.equal(hint(f).textContent, state === "ERROR" ? "统计不可用，可重试。" : "正在统计当前条件…");
    }
});

test("both real controls retain exact copy/layout and send/launch never consult cache", () => {
    for (const prefix of ["batchConfigEditor", "batchManual"]) {
        assert.ok(index.includes('<strong id="' + prefix + 'RecipientStatus">尚未统计</strong><span>页面仅加载已有缓存；点击按钮后重新统计当前条件。</span>'));
        assert.ok(index.includes('id="' + prefix + 'RecipientPreviewBtn">统计并缓存</button>'));
        assert.ok(index.includes('class="batch-config-editor-hint" id="' + prefix + 'RecipientHint"'));
    }
    for (const name of ["handleManualExecute", "confirmManualExecution", "saveBatchConfigEditor"]) assert.doesNotMatch(fn(name), /recipientStatistics|RecipientPreview|totalSendable|cacheOnly/);
    assert.match(fn("confirmManualExecution"), /buildManualExecutionSnapshot\(\)/);
    assert.match(fn("refreshBatchGateState"), /gateSeq !== st.requestSeq/);
    assert.match(fn("scheduleRecipientPreview"), /batchGateState\[kind\]\.pending/);
    const render = fn("renderRecipientStatistics");
    assert.doesNotMatch(render, /style=/);
    const classes = [...render.matchAll(/class=\\"([^\\]+)\\"/g)].map(match => match[1]);
    assert.ok(classes.length > 0);
    assert.ok(classes.every(name => ["batch-gate-warnline", "batch-gate-excluded"].includes(name)));
    assert.ok(!source.includes('recipients/preview"'), "UI statistics no longer computes gate comparison previews");
});
