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

function createTagOptionsSandbox() {
    const requests = [];
    const sandbox = {
        state: {
            aiTraining: {
                expertTagOptions: [],
                inboundTagOptions: [],
                selectedExpertTag: "",
                selectedInboundTagKey: ""
            }
        },
        expertTagLabels: {
            discovered: "新发现",
            verified: "已验证"
        },
        api: async (url) => {
            requests.push(url);
            if (url.includes("level=CANDIDATE")) {
                return [{ tag: "discovered", count: 2 }];
            }
            if (url.includes("level=APPLICATION")) {
                return [{ tag: "verified", count: 5 }];
            }
            if (url.includes("/inbound-summary/tags/options")) {
                return { items: [{ tagKey: "qa:1", label: "Funding", count: 1 }] };
            }
            throw new Error("unexpected url: " + url);
        },
        requests,
        $: () => ({ innerHTML: "" })
    };

    vm.createContext(sandbox);
    vm.runInContext(`
        function escapeHtml(v) {
            return String(v == null ? "" : v);
        }
    `, sandbox);
    vm.runInContext(extractFn("mergeExpertTagAggregations"), sandbox);
    vm.runInContext(extractFn("renderAiTrainingTagPills"), sandbox);
    vm.runInContext(extractFn("loadAiTrainingTagOptions"), sandbox);
    return sandbox;
}

describe("mergeExpertTagAggregations (P1-2)", () => {
    it("includes CANDIDATE-only tags and sums duplicate counts", () => {
        const sb = createTagOptionsSandbox();
        const merged = sb.mergeExpertTagAggregations([
            [{ tag: "discovered", count: 2 }],
            [{ tag: "discovered", count: 3 }, { tag: "verified", count: 5 }]
        ]);
        assert.strictEqual(merged.length, 2);
        const discovered = merged.find((item) => item.tag === "discovered");
        const verified = merged.find((item) => item.tag === "verified");
        assert.strictEqual(discovered.count, 5);
        assert.strictEqual(verified.count, 5);
    });
});

describe("loadAiTrainingTagOptions (P1-2)", () => {
    it("loads CANDIDATE and APPLICATION aggregations into expertTagOptions", async () => {
        const sb = createTagOptionsSandbox();
        await sb.loadAiTrainingTagOptions();

        assert.ok(sb.requests.some((url) => url.includes("level=CANDIDATE")));
        assert.ok(sb.requests.some((url) => url.includes("level=APPLICATION")));
        const tags = sb.state.aiTraining.expertTagOptions.map((item) => item.tag);
        assert.ok(tags.includes("discovered"), "CANDIDATE-only tag must appear");
        assert.ok(tags.includes("verified"), "APPLICATION tag must appear");
    });
});
