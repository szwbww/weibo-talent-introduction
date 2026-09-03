const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsPath = path.join(root, "app.js");
const indexPath = path.join(root, "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const html = fs.readFileSync(indexPath, "utf-8");

const CACHE_KEY = "20260902-rag-prompt-console";

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function makeSandbox() {
    const sandbox = {};
    vm.createContext(sandbox);
    vm.runInContext(extractFn("buildManualReplySubject"), sandbox);
    return sandbox;
}

describe("manual reply subject prefill (p3)", () => {
    it("I-1: buildManualReplySubject mirrors the server buildReplySubject rules (9 cases)", () => {
        const sandbox = makeSandbox();
        const build = sandbox.buildManualReplySubject;

        // rule 4: plain subject -> "Re: " + subject
        assert.strictEqual(build("Application for the talent programme"),
            "Re: Application for the talent programme");
        // rule 3: already Re:-prefixed -> as is (mixed case)
        assert.strictEqual(build("Re: Application"), "Re: Application");
        // rule 3: uppercase prefix kept verbatim
        assert.strictEqual(build("RE: Application"), "RE: Application");
        // rule 3: lowercase prefix kept verbatim
        assert.strictEqual(build("re: Application"), "re: Application");
        // rule 1: surrounding whitespace trimmed before prefixing
        assert.strictEqual(build("  Application  "), "Re: Application");
        // rule 2: empty / blank / null / undefined -> "Re:"
        assert.strictEqual(build(""), "Re:");
        assert.strictEqual(build("   "), "Re:");
        assert.strictEqual(build(null), "Re:");
        assert.strictEqual(build(undefined), "Re:");
        // rule 3 boundary: "Re" not followed by ":" is not a prefix
        assert.strictEqual(build("Reply about funding"), "Re: Reply about funding");
    });

    it("I-2: the result is truncated to 255 characters via slice(0, 255)", () => {
        const sandbox = makeSandbox();
        const longSubject = "x".repeat(300);
        assert.strictEqual(sandbox.buildManualReplySubject(longSubject).length, 255);
        assert.strictEqual(sandbox.buildManualReplySubject(longSubject), "Re: " + "x".repeat(251));
    });

    it("I-4: ${...} placeholders pass through verbatim, with no replace logic in the function", () => {
        const sandbox = makeSandbox();
        const withPlaceholder = "Fwd: ${expertName} 的申请";
        assert.strictEqual(sandbox.buildManualReplySubject(withPlaceholder),
            "Re: Fwd: ${expertName} 的申请");
        const fnBody = extractFn("buildManualReplySubject");
        assert.ok(!fnBody.includes("replace"), "buildManualReplySubject must not rewrite placeholders");
    });

    it("I-3/S-1: the subject input renders the escaped prefill value in one verbatim line", () => {
        assert.match(appJsSource,
            /<input id="manualReplySubject" placeholder="邮件主题" value="\$\{escapeHtml\(buildManualReplySubject\(record\.subject\)\)\}" style="margin-bottom:8px;">/,
            "the input line must match the S-1 contract verbatim");
    });

    it("I-3: manualReplySubject appears exactly 2x in app.js (render + read), with no post-render assignment", () => {
        assert.strictEqual((appJsSource.match(/manualReplySubject/g) || []).length, 2,
            "manualReplySubject must appear exactly 2 times in app.js (render + read)");
        assert.ok(!/manualReplySubject[^;\n]*\.value\s*=/.test(appJsSource),
            "app.js must not assign manualReplySubject.value after render");
        const lines = appJsSource.split("\n");
        for (const line of lines) {
            assert.ok(!(line.includes("manualReplySubject") && line.includes("addEventListener")),
                "no addEventListener may target manualReplySubject: " + line.trim());
        }
    });

    it("I-5: the cache-key triad uses one current value, three sites, one value", () => {
        const keys = (html.match(/\?v=[^"]+/g) || []).map((k) => k.slice(3));
        assert.strictEqual(keys.length, 3, "index.html must carry exactly three cache-busted asset URLs");
        assert.strictEqual(new Set(keys).size, 1, "all three keys must share one value");
        assert.strictEqual(keys[0], CACHE_KEY, "the shared key must be " + CACHE_KEY);
    });
});
