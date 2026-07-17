const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtmlSource = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createSandbox() {
    const apiCalls = [];
    const store = new Map();
    const qaRulesTable = { innerHTML: "" };
    store.set("qaRulesTable", qaRulesTable);
    const sandbox = {
        state: {
            qaRules: [
                {
                    id: 1,
                    displayName: "Fee fact",
                    categoryName: "Finance",
                    keywords: "fee,cost",
                    answerBody: "The application does not charge experts a service fee.",
                    replyBody: "Legacy reply body should not appear in table preview.",
                    priority: 10,
                    replyPolicy: "AUTO",
                    enabled: true
                },
                {
                    id: 2,
                    displayName: "Internal fact",
                    categoryName: "Finance",
                    keywords: "legacy",
                    answerBody: "Internal only.",
                    replyBody: "Internal only.",
                    priority: 20,
                    replyPolicy: "NEVER",
                    enabled: true
                }
            ],
            categories: [{ id: 1, categoryName: "Finance" }],
            selectedRuleId: null
        },
        api: async (url) => {
            apiCalls.push(url);
            if (url === "/api/qa/categories") return sandbox.state.categories;
            if (url === "/api/qa/rules") return sandbox.state.qaRules;
            throw new Error("unexpected url: " + url);
        },
        apiCalls,
        $: (sel) => {
            if (sel === "#qaRulesTable") {
                return qaRulesTable;
            }
            if (sel === "#qaRuleForm") {
                return {
                    categoryId: { value: "", innerHTML: "" },
                    reset() {}
                };
            }
            return { innerHTML: "", hidden: true, textContent: "" };
        },
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        badge: (text, tone) => `<span class="badge ${tone}">${text}</span>`,
        renderQaAuditPanel: () => {}
    };
    vm.createContext(sandbox);
    ["qaReplyPolicyBadge", "qaFactBodyPreview", "renderQaRulesTable", "loadQa"].forEach((name) => {
        vm.runInContext(extractFn(name), sandbox);
    });
    sandbox.__qaRulesTable = qaRulesTable;
    return sandbox;
}

describe("qa fact card editor", () => {
    it("index modal uses answerBody and replyPolicy select", () => {
        assert.ok(indexHtmlSource.includes('name="answerBody"'));
        assert.ok(indexHtmlSource.includes('name="replyPolicy"'));
        assert.ok(indexHtmlSource.includes('value="AUTO"'));
        assert.ok(indexHtmlSource.includes('value="REVIEW"'));
        assert.ok(indexHtmlSource.includes('value="NEVER"'));
        assert.ok(!indexHtmlSource.includes('name="replyBody"'));
        assert.ok(!indexHtmlSource.includes('name="autoReplyEnabled"'));
        assert.ok(!indexHtmlSource.includes('name="handoffRequired"'));
        assert.ok(indexHtmlSource.includes("<th>回复策略</th>"));
    });

    it("loadQa does not request coverage-keys endpoint", async () => {
        const sb = createSandbox();
        await sb.loadQa();
        assert.ok(!sb.apiCalls.includes("/api/qa/coverage-keys"));
        assert.deepEqual(sb.apiCalls, ["/api/qa/categories", "/api/qa/rules"]);
    });

    it("renderQaRulesTable shows policy badges", () => {
        const sb = createSandbox();
        sb.renderQaRulesTable();
        const html = sb.__qaRulesTable.innerHTML;
        assert.ok(html.includes("Fee fact"));
        assert.ok(html.includes("The application does not charge experts a service fee."));
        assert.ok(!html.includes("Legacy reply body"));
        assert.ok(html.includes('class="badge ok"'));
        assert.ok(html.includes("AUTO"));
        assert.ok(html.includes('class="badge error"'));
        assert.ok(html.includes("NEVER"));
    });

    it("saveQaRule sends replyPolicy", () => {
        const saveFn = extractFn("saveQaRule");
        assert.match(saveFn, /replyPolicy/);
        assert.doesNotMatch(saveFn, /autoReplyEnabled/);
        assert.doesNotMatch(saveFn, /handoffRequired/);
    });

    it("fillQaRuleForm round-trips replyPolicy", () => {
        const fillFn = extractFn("fillQaRuleForm");
        assert.match(fillFn, /replyPolicy/);
        assert.match(fillFn, /"REVIEW"/);
    });

    it("app.js does not reference legacy route badge", () => {
        assert.ok(!appJsSource.includes("qaLegacyRouteBadge"));
        assert.ok(!appJsSource.includes('name="replyBody"'));
    });

    it("reply snippet variant helpers remain available", () => {
        assert.ok(appJsSource.includes("function renderContentVariantRows"));
        assert.ok(appJsSource.includes("replySnippetForm"));
        assert.ok(appJsSource.includes("collectContentVariants"));
    });
});
