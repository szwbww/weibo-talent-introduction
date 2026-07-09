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

function createTextarea(value) {
    return {
        value,
        selectionStart: 0,
        selectionEnd: 0,
        dataset: {},
        focus() {},
        dispatchEvent() {}
    };
}

function createSandbox(activeElement) {
    const sandbox = {
        document: { activeElement },
        Event: function Event() {},
        state: {
            variableMeta: []
        },
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        EXPERT_VAR_KEY_SET: new Set([
            "expertName", "expertFamilyName", "researchFields", "institution", "keyword",
            "expertCountry", "employment", "hIndex", "worksCount", "lastPublicationYear",
            "degree", "recentWorkTitle", "patentTitle"
        ]),
        SENDER_VAR_KEY_SET: new Set([
            "senderEmail", "senderName", "senderTitle", "teamName", "countryName", "senderDisplayName"
        ])
    };
    vm.createContext(sandbox);
    [
        "rememberVarSelection",
        "resolveVarInsertRange",
        "insertVarAtCursor",
        "placeholderDefaultFallback",
        "renderVarChipButtons",
        "renderVarInsertMenuContent"
    ].forEach((name) => {
        vm.runInContext(extractFn(name), sandbox);
    });
    return sandbox;
}

describe("insertVarAtCursor unfocused append (I-2)", () => {
    it("appends at end when target never focused / no saved selection", () => {
        const textarea = createTextarea("hello");
        textarea.selectionStart = 0;
        textarea.selectionEnd = 0;
        const sandbox = createSandbox(null);
        sandbox.insertVarAtCursor(textarea, "${name}", 0);
        assert.strictEqual(textarea.value, "hello${name}");
        assert.strictEqual(textarea.selectionStart, "hello${name}".length);
        assert.strictEqual(textarea.selectionEnd, "hello${name}".length);
    });

    it("uses current selection when target is focused", () => {
        const textarea = createTextarea("hello");
        textarea.selectionStart = 2;
        textarea.selectionEnd = 2;
        const sandbox = createSandbox(textarea);
        sandbox.insertVarAtCursor(textarea, "${name}", 0);
        assert.strictEqual(textarea.value, "he${name}llo");
    });

    it("uses remembered selection when target is not focused", () => {
        const textarea = createTextarea("hello");
        textarea.selectionStart = 3;
        textarea.selectionEnd = 3;
        const sandbox = createSandbox(null);
        sandbox.rememberVarSelection(textarea);
        textarea.selectionStart = 0;
        textarea.selectionEnd = 0;
        sandbox.insertVarAtCursor(textarea, "${name}", 0);
        assert.strictEqual(textarea.value, "hel${name}lo");
    });

    it("renders metadata keys outside sender and expert groups", () => {
        const sandbox = createSandbox(null);
        sandbox.state.variableMeta = [
            { key: "senderName", label: "发件人姓名", nullable: false },
            { key: "expertName", label: "专家姓名", nullable: false },
            { key: "unsubscribeUrl", label: "退订链接", nullable: false }
        ];

        const html = sandbox.renderVarInsertMenuContent("body");

        assert.match(html, /data-var-key="unsubscribeUrl"/);
        assert.match(html, /退订链接/);
    });
});
