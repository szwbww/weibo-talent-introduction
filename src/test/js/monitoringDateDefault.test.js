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

function shanghaiTodayString() {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(new Date());
}

function shiftDays(dateStr, deltaDays) {
    const [y, m, d] = dateStr.split("-").map(Number);
    const shifted = new Date(Date.UTC(y, m - 1, d + deltaDays));
    return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, "0")}-${String(shifted.getUTCDate()).padStart(2, "0")}`;
}

function createMonitoringSandbox(initialDate, rangeDays = 7) {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                value: "",
                innerHTML: "",
                textContent: ""
            });
        }
        return store.get(id);
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        $$: () => [],
        URLSearchParams,
        state: {
            monitoring: {
                date: initialDate,
                rangeDays,
                page: 0,
                pageSize: 20,
                subTab: "introductions"
            }
        },
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;")
    };

    vm.createContext(sandbox);
    vm.runInContext(extractFn("monitoringToday"), sandbox);
    vm.runInContext(extractFn("shiftMonitoringDate"), sandbox);
    vm.runInContext(extractFn("monitoringWindowParams"), sandbox);
    vm.runInContext(extractFn("monitoringRangeParams"), sandbox);
    return sandbox;
}

function createProviderDistributionSandbox({ rows, unattributedBounceCount }) {
    // DOM-level stub：renderMonitoringProviderDistribution 只写 thead/tbody 的 innerHTML
    const parts = { thead: { innerHTML: "" }, tbody: { innerHTML: "" } };
    const sandbox = {
        $: () => ({ querySelector: (sel) => parts[sel.replace(/^#/, "")] }),
        state: {
            monitoring: {
                providerDistribution: rows,
                unattributedBounceCount
            }
        },
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        formatPercent: (v) => (v == null || Number.isNaN(v) ? "0%" : `${(v * 100).toFixed(1)}%`),
        monitoringDistributionBar: () => '<div class="bar-stub"></div>',
        monitoringReplyRateCell: () => '<span class="text-muted">-</span>'
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderMonitoringProviderDistribution"), sandbox);
    return { sandbox, parts };
}

const UNDELIVERED_FOOTER = "另有 3 封退信未能关联到专家（关联为空或专家已不存在），未计入上表任何一行。";

function providerRow(overrides = {}) {
    return Object.assign({
        provider: "gmail",
        sentCount: 10,
        repliedCount: 4,
        replyRate: 0.4,
        matureCohortCount: 8,
        matureRepliedCount: 3,
        matureReplyRate: 0.375,
        undeliveredCount: 2
    }, overrides);
}

describe("provider distribution unattributed footer", () => {
    it("renders the footer after present data rows when unattributed count is positive", () => {
        const { sandbox, parts } = createProviderDistributionSandbox({
            rows: [providerRow()],
            unattributedBounceCount: 3
        });
        sandbox.renderMonitoringProviderDistribution();
        const body = parts.tbody.innerHTML;
        assert.ok(body.includes("<strong>gmail</strong>"), "data row must render");
        assert.ok(body.includes(UNDELIVERED_FOOTER), "footer must render after data rows");
        assert.ok(
            body.indexOf(UNDELIVERED_FOOTER) > body.indexOf("gmail"),
            "footer must come after the data row"
        );
        assert.ok(!body.includes("暂无数据"), "no empty-state row when data rows exist");
    });

    it("omits the footer when unattributed count is zero", () => {
        const { sandbox, parts } = createProviderDistributionSandbox({
            rows: [providerRow()],
            unattributedBounceCount: 0
        });
        sandbox.renderMonitoringProviderDistribution();
        const body = parts.tbody.innerHTML;
        assert.ok(body.includes("<strong>gmail</strong>"), "data row must render");
        assert.ok(!body.includes("未能关联到专家"), "footer must be absent when count is 0");
    });
});

describe("monitoring date default", () => {
    it("initializes monitoring state date and range window", () => {
        assert.match(
            appJsSource,
            /monitoring:\s*\{\s*date:\s*monitoringToday\(\)/,
            "state.monitoring.date should use monitoringToday()"
        );
        assert.match(
            appJsSource,
            /monitoring:\s*\{\s*date:\s*monitoringToday\(\),\s*rangeDays:\s*7/,
            "state.monitoring should default rangeDays to 7"
        );
        assert.doesNotMatch(
            appJsSource,
            /monitoringRangeParams[\s\S]*toISOString\(\)\.slice\(0,\s*10\)/,
            "monitoringRangeParams should not fall back to UTC ISO date"
        );
    });

    it("monitoringToday returns Asia/Shanghai calendar date", () => {
        const sandbox = createMonitoringSandbox(null);
        assert.equal(sandbox.monitoringToday(), shanghaiTodayString());
    });

    it("monitoringRangeParams uses Shanghai today when date is unset", () => {
        const sandbox = createMonitoringSandbox(null);
        const params = sandbox.monitoringRangeParams();
        const today = shanghaiTodayString();
        assert.equal(params.get("from"), shiftDays(today, -6));
        assert.equal(params.get("to"), today);
    });

    it("monitoringRangeParams keeps explicit monitoring date and derives 7-day window", () => {
        const sandbox = createMonitoringSandbox("2026-06-21");
        const params = sandbox.monitoringRangeParams();
        assert.equal(params.get("to"), "2026-06-21");
        assert.equal(params.get("from"), "2026-06-15");
    });

    it("monitoringRangeParams collapses window to the anchor day for rangeDays 1", () => {
        const sandbox = createMonitoringSandbox("2026-06-21", 1);
        const params = sandbox.monitoringRangeParams();
        assert.equal(params.get("from"), "2026-06-21");
        assert.equal(params.get("to"), "2026-06-21");
    });

    it("monitoringRangeParams spans 29 days for rangeDays 30", () => {
        const sandbox = createMonitoringSandbox("2026-06-21", 30);
        const params = sandbox.monitoringRangeParams();
        assert.equal(params.get("to"), "2026-06-21");
        assert.equal(params.get("from"), "2026-05-23");
    });

    it("monitoringRangeParams crosses month boundaries with pure calendar math", () => {
        const sandbox = createMonitoringSandbox("2026-03-02", 7);
        const params = sandbox.monitoringRangeParams();
        assert.equal(params.get("from"), "2026-02-24");
        assert.equal(params.get("to"), "2026-03-02");
    });

    it("loadMonitoring syncs monitoringDate input from state", () => {
        assert.match(
            appJsSource,
            /dateInput\.value\s*=\s*state\.monitoring\.date\s*\|\|\s*monitoringToday\(\)/,
            "loadMonitoring should sync #monitoringDate with state date"
        );
    });

    it("triggeredByLabels includes MANUAL", () => {
        assert.match(appJsSource, /MANUAL:\s*"批量发送"/);
    });

    it("overlap monitoring cards include hint text", () => {
        assert.match(appJsSource, /细分统计不可相加/);
    });
});
