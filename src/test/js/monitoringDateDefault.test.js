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

function createMonitoringSandbox(initialDate) {
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
    vm.runInContext(extractFn("monitoringRangeParams"), sandbox);
    return sandbox;
}

describe("monitoring date default", () => {
    it("initializes monitoring state date to Asia/Shanghai today", () => {
        assert.match(
            appJsSource,
            /monitoring:\s*\{\s*date:\s*monitoringToday\(\)/,
            "state.monitoring.date should use monitoringToday()"
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
        assert.equal(params.get("from"), today);
        assert.equal(params.get("to"), today);
    });

    it("monitoringRangeParams keeps explicit monitoring date", () => {
        const sandbox = createMonitoringSandbox("2026-06-21");
        const params = sandbox.monitoringRangeParams();
        assert.equal(params.get("from"), "2026-06-21");
        assert.equal(params.get("to"), "2026-06-21");
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
