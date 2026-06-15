const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const source = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}", "g");
    const matches = source.match(regex) || [];
    for (const m of matches) {
        if (m.startsWith("function " + name + "(")) return m;
    }
    throw new Error("Could not find " + name + " in app.js");
}

const sandbox = { console: { error: () => {} } };
vm.createContext(sandbox);
vm.runInContext(extractFn("escapeHtml"), sandbox);
vm.runInContext(extractFn("renderFilterReasonsTable"), sandbox);

const escapeHtml = sandbox.escapeHtml;
const renderFilterReasonsTable = sandbox.renderFilterReasonsTable;

const filterReasonLabels = {
    MISSING_ORCID: "缺少 ORCID",
    INVALID_EMAIL_FORMAT: "邮箱格式无效",
    DISPOSABLE_EMAIL: "一次性邮箱",
    NO_DOCTORAL_DEGREE: "无博士学位",
    AGE_EXCEEDED: "超龄",
    CHINESE_NATIONALITY: "中国国籍",
    H_INDEX_TOO_LOW: "H-Index 过低",
    CITATION_COUNT_TOO_LOW: "引用数过低",
    INACTIVE: "近期无发表",
    "EMAIL:NO_MX_RECORD": "邮箱 MX 记录不存在",
    "EMAIL:INVALID_FORMAT": "邮箱格式无效",
    "EMAIL:DISPOSABLE_EMAIL": "一次性邮箱域名",
    "EMAIL:EMPTY_EMAIL": "邮箱为空"
};

Object.defineProperty(sandbox, "filterReasonLabels", { value: filterReasonLabels, writable: true });
vm.runInContext("var filterReasonLabels = this.filterReasonLabels;", sandbox);

const { describe, it } = require("node:test");

describe("renderFilterReasonsTable (XSS safety)", () => {
    it("escapes <script> tags in unknown reason keys", () => {
        const reasons = { "<script>alert(1)</script>": 5 };
        const html = renderFilterReasonsTable(reasons);
        assert.ok(!html.includes("<script>"), "should not contain unescaped <script>");
        assert.ok(html.includes("&lt;script&gt;"), "should contain escaped script tag");
    });

    it("escapes HTML in known reason values (count is safe)", () => {
        const reasons = { "CHINESE_NATIONALITY": 100 };
        const html = renderFilterReasonsTable(reasons);
        assert.ok(html.includes("中国国籍"), "should show Chinese label for CHINESE_NATIONALITY");
        assert.ok(html.includes("100"), "should show count");
    });

    it("returns empty string for empty object", () => {
        assert.strictEqual(renderFilterReasonsTable({}), "");
    });

    it("returns empty string for null input", () => {
        assert.strictEqual(renderFilterReasonsTable(null), "");
    });

    it("renders known reason with label", () => {
        const reasons = { "MISSING_ORCID": 42, "AGE_EXCEEDED": 7 };
        const html = renderFilterReasonsTable(reasons);
        assert.ok(html.includes("缺少 ORCID"), "should show MISSING_ORCID label");
        assert.ok(html.includes("超龄"), "should show AGE_EXCEEDED label");
        assert.ok(html.includes("42"), "should show MISSING_ORCID count");
        assert.ok(html.includes("7"), "should show AGE_EXCEEDED count");
    });

    it("sorts by count descending", () => {
        var reasons = { "三": 1, "一": 100, "二": 50 };
        var html = renderFilterReasonsTable(reasons);
        var pos100 = html.indexOf("100");
        var pos50 = html.indexOf("50");
        var pos1 = html.indexOf('">1</td>');
        if (pos1 === -1) pos1 = html.indexOf(">1<");
        assert.ok(pos100 < pos50, "100 should appear before 50");
        assert.ok(pos50 < pos1, "50 should appear before 1");
    });
});

describe("escapeHtml for progress messages", () => {
    it("escapes <script> tags", () => {
        const input = "<script>alert('xss')</script>";
        const out = escapeHtml(input);
        assert.ok(!out.includes("<script>"), "should not contain unescaped script tag");
        assert.ok(out.includes("&lt;script&gt;"), "should escape opening script tag");
    });

    it("escapes <img> onerror vectors", () => {
        const input = "<img src=x onerror=alert(1)>";
        const out = escapeHtml(input);
        assert.ok(!out.includes("<img"), "should not contain unescaped img tag");
        assert.ok(out.includes("&lt;img"), "should escape img tag");
    });

    it("preserves normal text", () => {
        const input = "批次 5: 已处理 100/500";
        const out = escapeHtml(input);
        assert.strictEqual(out, input);
    });

    it("handles empty string", () => {
        assert.strictEqual(escapeHtml(""), "");
    });
});
