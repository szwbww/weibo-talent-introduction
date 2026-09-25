const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("node:assert/strict");
const { test } = require("node:test");

const root = path.join(__dirname, "../../main/resources/static");
const source = fs.readFileSync(path.join(root, "app.js"), "utf8");
const markup = fs.readFileSync(path.join(root, "index.html"), "utf8");
function extractFn(name) {
    const match = source.match(new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}"));
    assert.ok(match, `missing ${name}`);
    return match[0];
}
const names = ["monitoringWindowParams", "shiftMonitoringDate", "loadMonitoringSubTab", "renderMonitoringActivityTable", "renderMonitoringPagination", "loadOpenTrackingSettings", "saveOpenTrackingSettings", "renderOpenTrackingSettings", "loadOpenTrackingRecords", "renderOpenTrackingRecords", "renderOpenTrackingPagination", "loadOpenTrackingDetail", "closeOpenTrackingDetail", "renderOpenTrackingDetail", "openTrackingVisible", "bindMonitoringEvents"];
function deferred() {
    let resolve, reject;
    const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
    return { promise, resolve, reject };
}
function setup() {
    const elements = new Map();
    const el = id => {
        if (!elements.has(id)) elements.set(id, {
            value: "", checked: false, disabled: false, hidden: false, textContent: "", innerHTML: "", listeners: {},
            addEventListener(type, handler) { this.listeners[type] = handler; },
            querySelector: selector => el(id + selector)
        });
        return elements.get(id);
    };
    const requests = [];
    const state = { view: "monitoring", monitoring: {
        date: "2026-09-25", rangeDays: 7, subTab: "tracking", page: 0, pageSize: 20,
        rows: [], totalCount: 0, openTracking: {
            settings: null, loading: false, saving: false, summary: null, rows: [], totalCount: 0,
            page: 0, status: "ALL", keyword: "", requestSeq: 0, settingsSeq: 0, detailSeq: 0,
            detailId: null, refreshedAt: null
        }
    } };
    const sandbox = {
        state, URLSearchParams, Date,
        $: selector => el(selector.replace(/^#/, "")),
        api: (url, options) => {
            const call = deferred();
            requests.push({ url, options, ...call });
            return call.promise;
        },
        escapeHtml: value => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        formatPercent: value => `${(value * 100).toFixed(1)}%`,
        monitoringToday: () => "2026-09-25",
        monitoringRangeParams: () => new URLSearchParams(),
        showStatus: () => {}
    };
    vm.createContext(sandbox);
    for (const name of names) vm.runInContext(extractFn(name), sandbox);
    vm.runInContext('const openTrackingStatusLabels = { OPENED: ["已收到打开信号", "ok"], NO_SIGNAL: ["暂无打开信号", "warn"], NOT_TRACKED: ["未跟踪", ""] };', sandbox);
    return { state, el, requests, sandbox };
}
const snapshot = (records = [], totalCount = records.length, trackedSent = 2, opened = 1) => ({
    records, totalCount, summary: { trackedSent, opened, openSignalRate: trackedSent ? opened / trackedSent : null }
});
const row = (id, overrides = {}) => ({ mailRecordId: id, expertName: "专家", recipient: null, senderAccountCode: "a",
    subject: "Subject", sentAt: "2026-09-25T10:00:00", trackingStatus: "NO_SIGNAL", firstOpenAt: null, lastOpenAt: null, ...overrides });

test("settings failures restore server value; no configuration prevents enabling but permits disabling", async () => {
    const { state, el, requests, sandbox: s } = setup();
    const loading = s.loadOpenTrackingSettings();
    assert.equal(el("motEnabled").disabled, true);
    requests[0].reject(new Error("network"));
    await loading;
    assert.equal(el("motEnabled").disabled, true);
    assert.equal(el("motSettingsRetry").hidden, false);
    const retry = s.loadOpenTrackingSettings();
    requests[1].resolve({ enabled: true, configured: false });
    await retry;
    assert.equal(el("motEnabled").checked, true);
    assert.equal(el("motEnabled").disabled, false);
    el("motEnabled").checked = false;
    const saving = s.saveOpenTrackingSettings(false);
    assert.equal(el("motEnabled").disabled, true);
    assert.deepEqual(JSON.parse(requests[2].options.body), { enabled: false });
    requests[2].reject(new Error("unavailable"));
    await saving;
    assert.equal(el("motEnabled").checked, true);
    assert.match(el("motSettingStatus").textContent, /unavailable/);
    const close = s.saveOpenTrackingSettings(false);
    requests[3].resolve({ enabled: false, configured: false });
    await close;
    assert.equal(el("motEnabled").checked, false);
    assert.equal(el("motEnabled").disabled, true);
    assert.match(el("motConfigStatus").textContent, /未配置/);
    assert.equal(state.monitoring.openTracking.settings.enabled, false);
});

test("records are snapshot-isolated, encode keyword, use 20 rows and retain summary when status changes", async () => {
    const { state, el, requests, sandbox: s } = setup();
    el("monitoringSenderAccount").value = "a&b";
    const old = s.loadOpenTrackingRecords();
    const first = new URL(requests[0].url, "http://localhost").searchParams;
    assert.equal(first.get("pageSize"), "20");
    assert.equal(first.get("senderAccountCode"), "a&b");
    state.monitoring.openTracking.status = "OPENED";
    state.monitoring.openTracking.keyword = "x & 中";
    const latest = s.loadOpenTrackingRecords();
    const second = new URL(requests[1].url, "http://localhost").searchParams;
    assert.equal(second.get("keyword"), "x & 中");
    assert.equal(second.get("status"), "OPENED");
    requests[1].resolve(snapshot([row(2)], 21));
    await latest;
    requests[0].resolve(snapshot([row(1)], 44, 99, 50));
    await old;
    assert.match(el("motTabletbody").innerHTML, /data-mot-detail="2"/);
    assert.doesNotMatch(el("motTabletbody").innerHTML, /data-mot-detail="1"/);
    assert.match(el("motMetrics").innerHTML, /50\.0%/);
    assert.equal(el("motPagination").innerHTML.includes("下一页"), true);
    const empty = s.loadOpenTrackingRecords();
    requests[2].resolve(snapshot([], 0, 0, 0));
    await empty;
    assert.match(el("motMetrics").innerHTML, /—/);
    assert.doesNotMatch(el("motMetrics").innerHTML, /NaN/);
    assert.match(el("motTabletbody").innerHTML, /暂无记录/);
});

test("tab departure and detail A-to-B race cannot overwrite current DOM; errors stay visible", async () => {
    const { state, el, requests, sandbox: s } = setup();
    const pending = s.loadOpenTrackingRecords();
    state.monitoring.subTab = "inbound";
    requests[0].resolve(snapshot([row(1)]));
    await pending;
    assert.doesNotMatch(el("motTabletbody").innerHTML, /data-mot-detail="1"/);
    state.monitoring.subTab = "tracking";
    const a = s.loadOpenTrackingDetail(1);
    const b = s.loadOpenTrackingDetail(2);
    requests[2].resolve(row(2, { trackingStatus: "OPENED", messageId: "<danger>" }));
    await b;
    requests[1].resolve(row(1));
    await a;
    assert.match(el("motDetail").innerHTML, /&lt;danger&gt;/);
    assert.doesNotMatch(el("motDetail").innerHTML, /<danger>/);
    s.closeOpenTrackingDetail();
    assert.equal(el("motDetail").hidden, true);
    const failing = s.loadOpenTrackingRecords();
    requests[3].reject(new Error("network"));
    await failing;
    assert.equal(el("motError").hidden, false);
    assert.match(el("motTabletbody").innerHTML, /加载失败/);
});

test("server-owned fields remain escaped and detail action accepts only positive integer IDs", async () => {
    const { el, requests, sandbox: s } = setup();
    for (const id of ["monitoringOpenTracking", "monitoringLegacyActivity", "motTable", "motDetail", "motEnabled", "motSettingsRetry", "motConfigStatus", "motRefreshed"]) {
        assert.match(markup, new RegExp(`id="${id}"`));
    }
    const req = s.loadOpenTrackingRecords();
    requests[0].resolve(snapshot([row(9, { expertName: '<img onerror="x">', subject: '<img src="/t/mail-open/x.gif">', senderAccountCode: "<b>x</b>", recipient: null })]));
    await req;
    const html = el("motTabletbody").innerHTML;
    assert.match(html, /&lt;img/);
    assert.doesNotMatch(html, /<img/);
    assert.match(html, /未保存收件快照/);
    assert.equal(s.loadOpenTrackingDetail("9<img>"), undefined);
    assert.equal(requests.length, 1);
});

test("deleted last page re-queries once at the last valid offset and never loops", async () => {
    const { state, el, requests, sandbox: s } = setup();
    state.monitoring.openTracking.page = 3;
    const loading = s.loadOpenTrackingRecords();
    assert.equal(new URL(requests[0].url, "http://localhost").searchParams.get("pageOffset"), "60");
    assert.match(el("motPagination").innerHTML, /disabled/);
    requests[0].resolve(snapshot([], 21));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(state.monitoring.openTracking.page, 1);
    assert.equal(new URL(requests[1].url, "http://localhost").searchParams.get("pageOffset"), "20");
    requests[1].resolve(snapshot([row(20)], 21));
    await loading;
    assert.match(el("motTabletbody").innerHTML, /data-mot-detail="20"/);
    assert.equal(requests.length, 2);
});

test("settings read racing a save must converge on the last server state", async () => {
    const { state, el, requests, sandbox: s } = setup();
    state.monitoring.openTracking.settings = { enabled: false, configured: true };
    const save = s.saveOpenTrackingSettings(true);
    const read = s.loadOpenTrackingSettings();
    requests[1].resolve({ enabled: false, configured: true });
    await read;
    requests[0].resolve({ enabled: true, configured: true });
    await save;
    assert.equal(requests[2].url, "/api/mail-open-tracking/settings");
    requests[2].resolve({ enabled: true, configured: true });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(el("motEnabled").checked, true);
    assert.equal(el("motEnabled").disabled, false);
});

test("query control resets pagination and reloads settings alongside encoded records", async () => {
    const { state, el, requests, sandbox: s } = setup();
    s.bindMonitoringEvents();
    state.monitoring.openTracking.page = 3;
    el("motKeyword").value = "李 & 邮";
    el("motStatus").value = "NOT_TRACKED";
    el("motQuery").listeners.click();
    assert.equal(state.monitoring.openTracking.page, 0);
    assert.equal(requests[0].url, "/api/mail-open-tracking/settings");
    const query = new URL(requests[1].url, "http://localhost").searchParams;
    assert.equal(query.get("pageOffset"), "0");
    assert.equal(query.get("keyword"), "李 & 邮");
    assert.equal(query.get("status"), "NOT_TRACKED");
    requests[0].resolve({ enabled: false, configured: true });
    requests[1].resolve(snapshot([row(7, { trackingStatus: "NOT_TRACKED" })]));
    await new Promise(resolve => setImmediate(resolve));
    assert.match(el("motTabletbody").innerHTML, /未跟踪/);
    assert.equal(el("motEnabled").disabled, false);
});

test("closing detail discards late response and 404 is distinguished from a transient failure", async () => {
    const { el, requests, sandbox: s } = setup();
    const old = s.loadOpenTrackingDetail(11);
    s.closeOpenTrackingDetail();
    requests[0].resolve(row(11));
    await old;
    assert.equal(el("motDetail").hidden, true);
    const missing = s.loadOpenTrackingDetail(12);
    requests[1].reject(Object.assign(new Error("not found"), { status: 404 }));
    await missing;
    assert.match(el("motDetail").innerHTML, /该邮件记录不存在/);
    assert.doesNotMatch(el("motDetail").innerHTML, /加载失败/);
});
