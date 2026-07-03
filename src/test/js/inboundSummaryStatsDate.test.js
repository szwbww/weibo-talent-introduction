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

function createSandbox() {
    const requests = [];
    const inputs = {
        "#inboundFrom": { value: "2026-04-04" },
        "#inboundTo": { value: "2026-07-03" }
    };
    const sandbox = {
        URLSearchParams,
        state: {
            inboundSummary: {
                from: "2026-04-04",
                to: "2026-07-03",
                stats: null,
                options: []
            }
        },
        api: async (url) => {
            requests.push(url);
            return url.includes("/tags/options")
                ? { items: [{ tagKey: "qa:5", label: "Duty and rights", count: 1 }] }
                : { items: [], total: 0 };
        },
        $: (selector) => inputs[selector] || { innerHTML: "" },
        renderTagBarChart: () => {},
        renderTagPieChart: () => {},
        renderInboundTagFilters: () => {},
        requests
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("inboundSummaryExclusiveToDate"), sandbox);
    vm.runInContext(extractFn("syncInboundSummaryDatesFromInputs"), sandbox);
    vm.runInContext(extractFn("inboundSummaryDateParams"), sandbox);
    vm.runInContext(extractFn("reloadInboundStats"), sandbox);
    return sandbox;
}

describe("inbound summary stats date scope", () => {
    it("loads stats and tag options with same date range as mail list", async () => {
        const sb = createSandbox();

        await sb.reloadInboundStats();

        assert.ok(sb.requests.some((url) => (
            url.includes("/api/inbound-summary/tags/stats?")
            && url.includes("from=2026-04-04T00%3A00%3A00")
            && url.includes("to=2026-07-04T00%3A00%3A00")
        )));
        assert.ok(sb.requests.some((url) => (
            url.includes("/api/inbound-summary/tags/options?")
            && url.includes("from=2026-04-04T00%3A00%3A00")
            && url.includes("to=2026-07-04T00%3A00%3A00")
        )));
    });
});
