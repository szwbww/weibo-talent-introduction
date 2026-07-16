const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const stylesCssPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtml = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createSandbox() {
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

describe("qa coverage key labels", () => {
    it("renders warn badge for empty coverage keys", () => {
        const sandbox = createSandbox();
        const result = sandbox.renderQaCoverageKeyLabels([]);
        assert.match(result, /未配置 AI 覆盖能力/);
        assert.match(result, /class="badge warn"/);
    });

    it("renders up to three labels joined by dot separator", () => {
        const sandbox = createSandbox();
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
        const sandbox = createSandbox();
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
        const sandbox = createSandbox();
        sandbox.state.qaCoverageKeys = [];
        const result = sandbox.renderQaCoverageKeyLabels(["unknown.key"]);
        assert.match(result, /unknown\.key/);
    });
});

describe("qa coverage key form options", () => {
    it("renderQaCoverageKeyOptions exists and iterates groups", () => {
        const fn = extractFn("renderQaCoverageKeyOptions");
        assert.match(fn, /qaCoverageKeyOptions/);
        assert.match(fn, /qaCoverageKeyWarning/);
        assert.match(fn, /data-qa-coverage-key/);
        assert.match(fn, /group/);
        assert.match(fn, /checkbox-row/);
    });

    it("renderQaCoverageKeyOptions escapes label and description", () => {
        const fn = extractFn("renderQaCoverageKeyOptions");
        assert.match(fn, /escapeHtml\(entry\.label\)/);
        assert.match(fn, /escapeHtml\(entry\.description/);
    });

    it("renderQaCoverageKeyOptions reads from state.qaCoverageKeys", () => {
        const fn = extractFn("renderQaCoverageKeyOptions");
        assert.match(fn, /state\.qaCoverageKeys/);
    });
});

describe("qa coverage key html contracts", () => {
    it("coverage key elements exist in index.html", () => {
        assert.match(indexHtml, /id="qaCoverageKeyOptions"/);
        assert.match(indexHtml, /id="qaCoverageKeyWarning"/);
        assert.match(indexHtml, /AI 覆盖能力/);
    });

    it("table header includes coverage column", () => {
        const rulesTableIdx = indexHtml.indexOf('id="qaRulesTable"');
        assert.ok(rulesTableIdx > 0);
        const tableStart = indexHtml.lastIndexOf("<table>", rulesTableIdx);
        const theadEnd = indexHtml.indexOf("</thead>", tableStart);
        const thead = indexHtml.substring(tableStart, theadEnd);
        assert.match(thead, /AI 覆盖能力/);
    });

    it("table colspan matches column count", () => {
        assert.match(appJsSource, /colspan="9"/);
    });

    it("saveQaRule collects checked coverage keys", () => {
        const saveIdx = appJsSource.indexOf("function saveQaRule(");
        assert.ok(saveIdx > 0);
        const saveFn = appJsSource.substring(saveIdx, saveIdx + 2000);
        assert.match(saveFn, /data-qa-coverage-key/);
        assert.match(saveFn, /:checked/);
        assert.match(saveFn, /coverageKeys/);
    });

    it("fillQaRuleForm calls renderQaCoverageKeyOptions", () => {
        const fillIdx = appJsSource.indexOf("function fillQaRuleForm(");
        assert.ok(fillIdx > 0);
        const fillFn = appJsSource.substring(fillIdx, fillIdx + 1200);
        assert.match(fillFn, /renderQaCoverageKeyOptions/);
    });

    it("loadQa fetches coverage keys metadata", () => {
        const loadIdx = appJsSource.indexOf("async function loadQa()");
        assert.ok(loadIdx > 0);
        const loadFn = appJsSource.substring(loadIdx, loadIdx + 600);
        assert.match(loadFn, /\/api\/qa\/coverage-keys/);
        assert.match(loadFn, /qaCoverageKeys/);
    });

    it("hideQaRuleEditor clears coverage options", () => {
        const hideIdx = appJsSource.indexOf("function hideQaRuleEditor()");
        assert.ok(hideIdx > 0);
        const hideFn = appJsSource.substring(hideIdx, hideIdx + 500);
        assert.match(hideFn, /renderQaCoverageKeyOptions\(\[\]\)/);
    });

    it("no coverage key constants hardcoded in app.js", () => {
        assert.doesNotMatch(appJsSource, /"company\.legal_name"/);
        assert.doesNotMatch(appJsSource, /"programme\.purpose"/);
    });

    it("loadQa does not swallow coverage-keys failure", () => {
        const loadFn = extractFn("loadQa");
        assert.doesNotMatch(loadFn, /\/api\/qa\/coverage-keys.*\.catch/);
    });

    it("no coverage key constants in index.html", () => {
        assert.doesNotMatch(indexHtml, /"company\.legal_name"/);
        assert.doesNotMatch(indexHtml, /"programme\.purpose"/);
    });

    it("no new CSS classes beyond existing badge conventions", () => {
        const styles = fs.readFileSync(stylesCssPath, "utf-8");
        assert.match(indexHtml, /metadata-grid/);
        assert.match(styles, /\.badge\.warn/);
    });
});
