const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtml = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createLabelSandbox() {
    const sandbox = {
        state: { qaCoverageKeys: [] },
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;"),
        badge: (label, type) => `<span class="badge ${type || "primary"}">${label}</span>`
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderQaCoverageKeyLabels"), sandbox);
    return sandbox;
}

describe("qa coverage key labels (legacy helper)", () => {
    it("renders warn badge for empty coverage keys", () => {
        const sandbox = createLabelSandbox();
        const result = sandbox.renderQaCoverageKeyLabels([]);
        assert.match(result, /未配置 AI 覆盖能力/);
        assert.match(result, /class="badge warn"/);
    });

    it("renders up to three labels joined by dot separator", () => {
        const sandbox = createLabelSandbox();
        sandbox.state.qaCoverageKeys = [
            { key: "company.legal_name", label: "公司法定名称", group: "公司信息" },
            { key: "company.registered_location", label: "公司注册地点", group: "公司信息" },
            { key: "finance.government_funding", label: "政府资金", group: "资金" }
        ];
        const result = sandbox.renderQaCoverageKeyLabels([
            "company.legal_name", "company.registered_location", "finance.government_funding"
        ]);
        assert.match(result, /公司法定名称/);
        assert.match(result, /公司注册地点/);
        assert.match(result, /政府资金/);
        assert.doesNotMatch(result, /另/);
    });

    it("shows overflow count for more than three keys", () => {
        const sandbox = createLabelSandbox();
        sandbox.state.qaCoverageKeys = [
            { key: "company.legal_name", label: "公司法定名称", group: "公司信息" },
            { key: "company.registered_location", label: "公司注册地点", group: "公司信息" },
            { key: "finance.government_funding", label: "政府资金", group: "资金" },
            { key: "finance.enterprise_compensation", label: "企业报酬", group: "资金" }
        ];
        const result = sandbox.renderQaCoverageKeyLabels([
            "company.legal_name", "company.registered_location",
            "finance.government_funding", "finance.enterprise_compensation"
        ]);
        assert.match(result, /公司法定名称/);
        assert.match(result, /另 1 项/);
    });

    it("falls back to raw key when label not found", () => {
        const sandbox = createLabelSandbox();
        sandbox.state.qaCoverageKeys = [];
        const result = sandbox.renderQaCoverageKeyLabels(["unknown.key"]);
        assert.match(result, /unknown\.key/);
    });
});

describe("fact-card era: coverage UI removed", () => {
    it("index.html has no coverage key editor elements", () => {
        assert.doesNotMatch(indexHtml, /id="qaCoverageKeyOptions"/);
        assert.doesNotMatch(indexHtml, /id="qaCoverageKeyWarning"/);
    });

    it("qa rules table uses fact title column not coverage column", () => {
        const rulesTableIdx = indexHtml.indexOf('id="qaRulesTable"');
        assert.ok(rulesTableIdx > 0);
        const tableStart = indexHtml.lastIndexOf("<table>", rulesTableIdx);
        const theadEnd = indexHtml.indexOf("</thead>", tableStart);
        const thead = indexHtml.substring(tableStart, theadEnd);
        assert.match(thead, /事实标题/);
        assert.doesNotMatch(thead, /AI 覆盖能力/);
    });

    it("loadQa does not fetch coverage-keys endpoint", () => {
        const loadFn = extractFn("loadQa");
        assert.doesNotMatch(loadFn, /\/api\/qa\/coverage-keys/);
    });

    it("saveQaRule does not send coverageKeys", () => {
        const saveFn = extractFn("saveQaRule");
        assert.doesNotMatch(saveFn, /coverageKeys/);
        assert.match(saveFn, /answerBody/);
    });

    it("fillQaRuleForm does not render coverage options", () => {
        const fillFn = extractFn("fillQaRuleForm");
        assert.doesNotMatch(fillFn, /renderQaCoverageKeyOptions/);
        assert.match(fillFn, /answerBody/);
    });

    it("table colspan matches nine-column fact-card layout", () => {
        assert.match(appJsSource, /colspan="9"/);
    });

    it("no coverage key constants hardcoded in app.js", () => {
        assert.doesNotMatch(appJsSource, /"company\.legal_name"/);
        assert.doesNotMatch(appJsSource, /"programme\.purpose"/);
    });
});
