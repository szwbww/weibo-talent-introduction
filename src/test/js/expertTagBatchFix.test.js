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

function extractConst(name) {
    const regex = new RegExp("const\\s+" + name + "\\s*=\\s*\\d+;");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find const " + name + " in app.js");
    return match[0];
}

function createBatchCollectSandbox() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, { id, value: "" });
        }
        return store.get(id);
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        state: { contactsTotalHits: 0 },
        api: async () => ({ experts: [] }),
        URLSearchParams
    };

    vm.createContext(sandbox);
    vm.runInContext(extractConst("ES_MAX_RESULT_WINDOW"), sandbox);
    vm.runInContext(extractConst("ES_PAGE_SIZE_MAX"), sandbox);
    vm.runInContext(extractFn("collectBatchMailContactIds"), sandbox);
    return sandbox;
}

function createTagFetchSandbox() {
    const sandbox = {
        api: async () => ({ tags: [] }),
        URLSearchParams
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("fetchExpertTagsFromEs"), sandbox);
    vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
    vm.runInContext(`
        const expertTagLabels = {
            auto_promoted: "自动晋升",
            verified: "已验证",
            discovered: "新发现",
            "承诺回复材料": "承诺回复材料"
        };
        function escapeHtml(v) {
            return String(v == null ? "" : v);
        }
    `, sandbox);
    return sandbox;
}

describe("collectBatchMailContactIds pagination (P1-1)", () => {
    it("requests from=0,size=1000 and from=1000,size=201 when totalHits=1201", async () => {
        const sb = createBatchCollectSandbox();
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#expertTagFilter").value = "承诺回复材料";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#expertRegionFilter").value = "";
        sb.state.contactsTotalHits = 1201;

        const requests = [];
        sb.api = async (url) => {
            requests.push(url);
            if (url.includes("from=0")) {
                return {
                    experts: Array.from({ length: 1000 }, (_, i) => ({
                        contactId: i % 2 === 0 ? i + 1 : null
                    }))
                };
            }
            if (url.includes("from=1000")) {
                return {
                    experts: Array.from({ length: 201 }, (_, i) => ({
                        contactId: (1000 + i) % 2 === 0 ? 1000 + i + 1 : null
                    }))
                };
            }
            throw new Error("unexpected url: " + url);
        };

        const contactIds = await sb.collectBatchMailContactIds();

        assert.strictEqual(requests.length, 2);
        assert.ok(requests[0].includes("from=0"));
        assert.ok(requests[0].includes("size=1000"));
        assert.ok(requests[1].includes("from=1000"));
        assert.ok(requests[1].includes("size=201"));
        assert.ok(requests[0].includes("tag="));
        assert.strictEqual(contactIds.length, 601);
    });

    it("throws when totalHits exceeds ES max_result_window", async () => {
        const sb = createBatchCollectSandbox();
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#expertTagFilter").value = "";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#expertRegionFilter").value = "";
        sb.state.contactsTotalHits = 10001;

        await assert.rejects(
            () => sb.collectBatchMailContactIds(),
            (err) => err.message.includes("10001") && err.message.includes("10000")
        );
    });

    it("throws when a page fetch fails", async () => {
        const sb = createBatchCollectSandbox();
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#expertTagFilter").value = "";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#expertRegionFilter").value = "";
        sb.state.contactsTotalHits = 1201;

        sb.api = async (url) => {
            if (url.includes("from=1000")) {
                throw new Error("network down");
            }
            return { experts: [{ contactId: 1 }] };
        };

        await assert.rejects(
            () => sb.collectBatchMailContactIds(),
            (err) => err.message.includes("已中止批量发送")
        );
    });
});

describe("fetchExpertTagsFromEs authoritative tags (P1-2)", () => {
    it("loads tags from /api/experts/profile instead of list cache", async () => {
        const sb = createTagFetchSandbox();
        let profileUrl = "";
        sb.api = async (url) => {
            profileUrl = url;
            return { orcidId: "0000-0001", tags: ["承诺回复材料"] };
        };

        const tags = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.ok(profileUrl.includes("/api/experts/profile"));
        assert.ok(profileUrl.includes("orcidId=0000-0001"));
        assert.ok(profileUrl.includes("level=CANDIDATE"));
        assert.deepStrictEqual(tags, ["承诺回复材料"]);
    });

    it("renders ES tags in editor even when list cache has no tags", () => {
        const sb = createTagFetchSandbox();
        const html = sb.renderExpertTagEditor(["承诺回复材料"], "0000-0001", "CANDIDATE");
        assert.ok(html.includes("承诺回复材料"));
        assert.ok(html.includes('data-orcid="0000-0001"'));
    });
});
