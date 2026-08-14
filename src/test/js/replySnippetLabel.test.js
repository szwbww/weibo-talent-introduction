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
        replySnippetTypeLabels: {
            SALUTATION: "尊语",
            ACK: "致谢语",
            GREETING: "开场白",
            CLOSING: "结束语",
            CUSTOM: "自定义内容"
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("replySnippetDisplayLabel"), sandbox);
    return sandbox;
}

describe("reply snippet display label", () => {
    it("uses the name when present", () => {
        const sb = createSandbox();
        assert.equal(
            sb.replySnippetDisplayLabel({ name: "教授尊称-正式", snippetType: "SALUTATION", id: 10, content: "Dear X," }),
            "教授尊称-正式"
        );
    });

    it("falls back to the content excerpt when name is empty or whitespace", () => {
        const sb = createSandbox();
        assert.equal(
            sb.replySnippetDisplayLabel({ name: "", snippetType: "SALUTATION", id: 10, content: "Dear Professor," }),
            "Dear Professor,"
        );
        assert.equal(
            sb.replySnippetDisplayLabel({ name: "   ", snippetType: "SALUTATION", id: 10, content: "Dear Professor," }),
            "Dear Professor,"
        );
    });

    it("returns the first line verbatim without ellipsis when it fits 40 chars", () => {
        const sb = createSandbox();
        const label = sb.replySnippetDisplayLabel({
            snippetType: "SALUTATION",
            id: 10,
            content: "Dear Professor,"
        });
        assert.equal(label, "Dear Professor,");
        assert.ok(!label.includes("…"));
    });

    it("truncates a first line longer than 40 chars to 40 plus ellipsis", () => {
        const sb = createSandbox();
        const label = sb.replySnippetDisplayLabel({
            snippetType: "GREETING",
            id: 10,
            content: "Thank you for your email. Please find our answers below."
        });
        assert.equal(label, "Thank you for your email. Please find ou…");
        assert.equal(label.length, 41);
    });

    it("skips leading blank lines and uses the first non-empty line", () => {
        const sb = createSandbox();
        const label = sb.replySnippetDisplayLabel({
            snippetType: "CLOSING",
            id: 10,
            content: "\n\nDear Professor,\nBest regards"
        });
        assert.equal(label, "Dear Professor,");
    });

    it("keeps EXCERPT_MAX_CHARS in sync between Kotlin and JS", () => {
        const ktPath = path.join(
            __dirname,
            "..",
            "..",
            "main",
            "kotlin",
            "com",
            "weibo",
            "talentintroduction",
            "template",
            "service",
            "MailComposeTemplateService.kt"
        );
        const ktSource = fs.readFileSync(ktPath, "utf-8");
        const match = ktSource.match(/EXCERPT_MAX_CHARS\s*=\s*(\d+)/);
        assert.ok(match, "EXCERPT_MAX_CHARS must be declared in MailComposeTemplateService.kt");
        assert.equal(Number(match[1]), 40, "Kotlin EXCERPT_MAX_CHARS must equal 40");
        assert.ok(appJsSource.includes("firstLine.length > 40"), "JS excerpt threshold must stay 40");
        assert.ok(appJsSource.includes("firstLine.slice(0, 40)"), "JS excerpt length must stay 40");
    });
});
