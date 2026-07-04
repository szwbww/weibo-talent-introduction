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
    const sandbox = {
        escapeHtml: (value) => String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
    };
    vm.createContext(sandbox);
    ["formatQaRuleOptionName", "buildUnmatchedQaReplyHtml"].forEach((name) => {
        vm.runInContext(extractFn(name), sandbox);
    });
    return sandbox;
}

describe("unmatched QA reply source (from app.js)", () => {
    const mailSendOptionsWithoutQa = [
        { optionType: "TEMPLATE", optionValue: "INTRODUCTION", optionName: "项目介绍邮件" },
        { optionType: "COMPOSE_TEMPLATE", optionValue: "5", optionName: "Funding FAQ Pack" }
    ];

    const enabledQaRules = [
        {
            id: 10,
            displayName: "Funding overview",
            replySubject: "Re: Funding",
            enabled: true
        },
        {
            id: 11,
            displayName: "Disabled rule",
            replySubject: "Re: Hidden",
            enabled: false
        }
    ];

    it("renders single-rule QA reply UI from enabled QA rules, not mail-send-options", () => {
        const sb = createSandbox();
        assert.strictEqual(mailSendOptionsWithoutQa.some((option) => option.optionType === "QA"), false);

        const html = sb.buildUnmatchedQaReplyHtml(enabledQaRules, 42);

        assert.ok(html.includes("QA 邮件回复（单规则）"));
        assert.ok(html.includes('id="unmatchedQaOption"'));
        assert.ok(html.includes('data-action="send-pending-qa-reply"'));
        assert.ok(html.includes('data-record-id="42"'));
        assert.ok(html.includes('value="10"'));
        assert.ok(html.includes("Funding overview"));
        assert.ok(!html.includes("Disabled rule"));
    });

    it("returns empty html when no enabled QA rules exist", () => {
        const sb = createSandbox();
        const html = sb.buildUnmatchedQaReplyHtml(
            [{ id: 99, displayName: "Off", enabled: false }],
            42
        );
        assert.strictEqual(html, "");
    });

    it("falls back to Rule #id label when displayName and replySubject are blank", () => {
        const sb = createSandbox();
        const html = sb.buildUnmatchedQaReplyHtml(
            [{ id: 77, displayName: "", replySubject: "", enabled: true }],
            1
        );
        assert.ok(html.includes("Rule #77"));
    });
});
