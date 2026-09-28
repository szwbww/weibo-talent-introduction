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
    vm.runInContext('const openTrackingStatusLabels = { OPENED: ["疑似打开（120秒后请求）", "info"], NO_SIGNAL: ["无120秒后请求", "warn"], NOT_TRACKED: ["未跟踪", ""] };', sandbox);
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

test("static markup pins the 120-second copy and drops every stale signal label", () => {
    assert.ok(markup.includes('<div id="motMetrics" class="card-grid" aria-live="polite"><div class="metric-card"><div class="metric-label">跟踪发出</div><div class="metric-value">—</div></div><div class="metric-card"><div class="metric-label">120秒后请求</div><div class="metric-value">—</div></div><div class="metric-card"><div class="metric-label">120秒后请求率</div><div class="metric-value">—</div></div></div>'), "three metric cards");
    assert.ok(markup.includes('<p class="muted">发送后120秒内的图片请求按预加载处理；之后的新请求仅表示疑似打开，不等于本人已读。图片可能被缓存，重复打开不一定产生新请求。指标按发送日期和发件账号统计；状态与搜索只影响列表。</p>'), "explanation paragraph");
    assert.ok(markup.includes('<select id="motStatus"><option value="ALL">全部</option><option value="OPENED">疑似打开（120秒后请求）</option><option value="NO_SIGNAL">无120秒后请求</option><option value="NOT_TRACKED">未跟踪</option></select>'), "status options");
    assert.ok(markup.includes('<thead><tr><th>发送时间</th><th>专家</th><th>收件邮箱</th><th>发件账号</th><th>主题</th><th>跟踪状态</th><th>首次图片请求</th><th>最近图片请求</th><th>操作</th></tr></thead>'), "nine columns");
    assert.match(source, /OPENED:\s*\["疑似打开（120秒后请求）",\s*"info"\]/);
    assert.match(source, /NO_SIGNAL:\s*\["无120秒后请求",\s*"warn"\]/);
    assert.match(source, /NOT_TRACKED:\s*\["未跟踪",\s*""\]/);
    for (const stale of ["已收到打开信号", "打开信号率", "首次信号", "最近信号"]) {
        assert.equal(markup.includes(stale), false, `index.html must not keep ${stale}`);
        assert.equal(source.includes(stale), false, `app.js must not keep ${stale}`);
    }
    assert.doesNotMatch(source, /OPENED:\s*\[[^\]]*"ok"/);
});

test("badges, dynamic metrics and detail labels render the 120-second copy", async () => {
    const { el, requests, sandbox: s } = setup();
    const pending = s.loadOpenTrackingRecords();
    requests[0].resolve(snapshot([
        row(1, { trackingStatus: "OPENED", firstOpenAt: "2026-09-25T10:00:10", lastOpenAt: "2026-09-25T10:02:01" }),
        row(2, { trackingStatus: "NO_SIGNAL" }),
        row(3, { trackingStatus: "NOT_TRACKED" })
    ], 3, 4, 2));
    await pending;
    const html = el("motTabletbody").innerHTML;
    assert.match(html, /<span class="badge info">疑似打开（120秒后请求）<\/span>/);
    assert.match(html, /<span class="badge warn">无120秒后请求<\/span>/);
    assert.match(html, /<span class="badge">未跟踪<\/span>/);
    assert.doesNotMatch(html, /badge ok/);
    const metrics = el("motMetrics").innerHTML;
    for (const label of ["跟踪发出", "120秒后请求", "120秒后请求率"]) {
        assert.ok(metrics.includes(`<div class="metric-label">${label}</div>`), `metric label ${label}`);
    }
    assert.ok(metrics.includes('<div class="metric-value">50.0%</div>'), "120-second request rate");
    const detail = s.loadOpenTrackingDetail(1);
    requests[1].resolve(row(1, { trackingStatus: "OPENED", firstOpenAt: "2026-09-25T10:00:10", lastOpenAt: "2026-09-25T10:02:01" }));
    await detail;
    const detailHtml = el("motDetail").innerHTML;
    assert.match(detailHtml, /<dt>首次图片请求<\/dt><dd>2026-09-25T10:00:10<\/dd>/);
    assert.match(detailHtml, /<dt>最近图片请求<\/dt><dd>2026-09-25T10:02:01<\/dd>/);
    assert.match(detailHtml, /<dt>状态<\/dt><dd>疑似打开（120秒后请求）<\/dd>/);
});

test("all eleven versioned assets in index.html carry one cache key", () => {
    const versions = [...markup.matchAll(/\.(?:css|js)\?v=([A-Za-z0-9._-]+)/g)].map(match => match[1]);
    assert.equal(versions.length, 11, "expected the five stylesheets and six scripts");
    assert.equal(new Set(versions).size, 1, `assets must share one cache key, saw ${[...new Set(versions)].join(", ")}`);
});
