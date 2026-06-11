const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const source = fs.readFileSync(appJsPath, "utf-8");

// Extract needed functions from app.js
function extractFn(name) {
    const regex = new RegExp("function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = source.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

const sandbox = {};
vm.createContext(sandbox);
// Load dependencies first: escapeHtml is used by renderDiscoverySummaryText
vm.runInContext(extractFn("escapeHtml"), sandbox);
vm.runInContext(extractFn("normalizeDiscoveryResultSummary"), sandbox);
vm.runInContext(extractFn("renderDiscoverySummaryText"), sandbox);

const normalize = sandbox.normalizeDiscoveryResultSummary;
const renderSummaryText = sandbox.renderDiscoverySummaryText;

const { describe, it } = require("node:test");

describe("normalizeDiscoveryResultSummary (from app.js)", () => {

    it("new structure preserves both bySource and summaryText", () => {
        const input = JSON.stringify({
            triggeredBy: "MANUAL",
            stats: { bySource: { EUROPE_PMC: { papersSearched: 5 } }, indexed: 3 },
            summaryText: "完成: 论文 5, 收录 3, 晋升 2"
        });
        const result = normalize(input);
        assert.strictEqual(result.summaryText, "完成: 论文 5, 收录 3, 晋升 2");
        assert.strictEqual(result.indexed, 3);
        assert.strictEqual(result.bySource.EUROPE_PMC.papersSearched, 5);
    });

    it("old structure returns bySource directly", () => {
        const input = JSON.stringify({ bySource: { EUROPE_PMC: { papersSearched: 5 } }, indexed: 3 });
        const result = normalize(input);
        assert.strictEqual(result.bySource.EUROPE_PMC.papersSearched, 5);
        assert.strictEqual(result.summaryText, undefined);
    });

    it("string and object inputs produce same result", () => {
        const obj = { stats: { bySource: { X: { count: 1 } } }, summaryText: "done" };
        assert.strictEqual(normalize(JSON.stringify(obj)).summaryText, "done");
        assert.strictEqual(normalize(obj).summaryText, "done");
    });

    it("null input returns null", () => {
        assert.strictEqual(normalize(null), null);
        assert.strictEqual(normalize(undefined), null);
    });

    it("malformed JSON string returns null", () => {
        assert.strictEqual(normalize("{bad json"), null);
    });
});

describe("renderDiscoverySummaryText (from app.js)", () => {

    it("produces escaped HTML for summary text", () => {
        const html = renderSummaryText("<b>bold</b> & ampersand");
        assert.ok(!html.includes("<b>"), "raw <b> tag must be escaped");
        assert.ok(html.includes("&lt;b&gt;"), "should contain escaped &lt;b&gt;");
        assert.ok(html.includes("&amp;"), "should contain escaped &amp;");
        assert.ok(!html.includes("<script"), "should not contain unescaped <script>");
    });

    it("escapes XSS payload in summaryText", () => {
        const xss = '<img src=x onerror=alert(1)>';
        const html = renderSummaryText(xss);
        assert.ok(!html.includes("<img"), "raw <img tag must be escaped");
        assert.ok(html.includes("&lt;img"), "should contain escaped &lt;img");
    });

    it("returns empty string for null/undefined summaryText", () => {
        assert.strictEqual(renderSummaryText(null), "");
        assert.strictEqual(renderSummaryText(undefined), "");
        assert.strictEqual(renderSummaryText(""), "");
    });
});
