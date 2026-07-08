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

function createStore() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                innerHTML: "",
                textContent: "",
                value: "",
                checked: false,
                elements: {},
                querySelector: () => null,
                querySelectorAll: () => []
            });
        }
        return store.get(id);
    }
    return { el, get: (id) => store.get(id) };
}

function createSandbox(blocks) {
    const store = createStore();
    const form = store.el("composeTemplateForm");
    form.subject = { value: "Professor ${expertFamilyName|Professor} - ${researchFields|Your Field}" };
    const sandbox = {
        state: {
            composeTemplates: [],
            qaRules: [],
            replySnippets: [],
            composeTemplatePreviewExperts: [],
            composeTemplatePreviewAccounts: [],
            selectedComposeTemplateId: null
        },
        composeTemplatePreviewRequestId: 0,
        $: (sel) => store.el(sel.replace(/^#/, "")),
        $$: (sel) => sel === "#composeTemplateBlocksList .compose-template-block-row" ? blocks : [],
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        composeBlockTypeLabels: {
            QA_RULE: "QA 规则",
            REPLY_SNIPPET: "回复片段",
            CUSTOM_TEXT: "自定义文本"
        },
        api: async () => ({
            subject: "Professor Professor - Your Field",
            body: "Dear Professor, from Chen Jingjing",
            blocks: [{ blockOrder: 0, blockType: "CUSTOM_TEXT", included: true }],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variables: [
                { key: "senderName", label: "发件人姓名", value: "Chen Jingjing", filled: true, usedFallback: false }
            ]
        })
    };
    vm.createContext(sandbox);
    [
        "placeholderDefaultFallback",
        "composeTemplatePreviewExpertLabel",
        "composeTemplatePreviewAccountLabel",
        "findComposeTemplatePreviewOption",
        "collectComposeTemplateBlocksFromForm",
        "collectComposeTemplatePreviewContext",
        "collectComposeTemplatePreviewSubjectVariants",
        "collectComposeTemplatePreviewSampleText",
        "refreshComposeTemplatePreview",
        "renderComposeTemplatePreviewHtml",
        "renderComposeTemplatePreviewVariableRows",
        "renderServerComposeTemplatePreviewPanel",
        "renderServerComposeTemplatePreview",
        "randomComposeTemplatePreviewExpert",
        "updateComposeTemplatePreviewMeta"
    ].forEach((name) => vm.runInContext(extractFn(name), sandbox));
    sandbox.__store = store;
    return sandbox;
}

function customTextRow(text) {
    return {
        querySelector(selector) {
            if (selector === '[data-field="blockType"]') return { value: "CUSTOM_TEXT" };
            if (selector === '[data-field="refId"]') return null;
            if (selector === '[data-field="customText"]') return { value: text };
            return null;
        }
    };
}

describe("compose template server preview", () => {
    it("renders server preview response in panel", () => {
        const sb = createSandbox([customTextRow("Dear ${expertFamilyName|Professor}, from ${senderName}")]);
        sb.renderServerComposeTemplatePreviewPanel({
            subject: "Professor Professor - Your Field",
            body: "Dear Professor, from Chen Jingjing",
            blocks: [{ blockOrder: 0, blockType: "CUSTOM_TEXT", included: true }],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variables: []
        });
        sb.__store.el("composeTemplatePreviewStatus").innerHTML = '<span class="preview-source-badge">服务端预览</span>';

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        const status = sb.__store.get("composeTemplatePreviewStatus").innerHTML;
        assert.ok(html.includes("Professor Professor - Your Field"));
        assert.ok(html.includes("Dear Professor, from Chen Jingjing"));
        assert.ok(html.includes("ada@mit.edu"));
        assert.ok(status.includes("preview-source-badge"));
        assert.ok(status.includes("服务端预览"));
    });

    it("strict placeholder mode shows skipped blocks from server", () => {
        const sb = createSandbox([
            customTextRow("Visible ${senderName}"),
            customTextRow("Hidden ${researchFields}")
        ]);
        sb.renderServerComposeTemplatePreviewPanel({
            subject: "Subject",
            body: "Visible Chen Jingjing",
            blocks: [
                { blockOrder: 0, blockType: "CUSTOM_TEXT", included: true },
                { blockOrder: 1, blockType: "CUSTOM_TEXT", included: false, skipReason: "存在未满足占位符" }
            ],
            fallbackKeys: ["researchFields"],
            toEmail: "expert@example.com",
            variables: []
        });

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("Visible Chen Jingjing"));
        assert.ok(!html.includes("Hidden"));
        assert.ok(html.includes("已跳过 1 段"));
    });

    it("refresh calls preview-draft endpoint", async () => {
        const sb = createSandbox([customTextRow("To ${expertName}")]);
        let called = false;
        sb.api = async (url) => {
            if (url === "/api/compose-templates/preview-draft") {
                called = true;
                return {
                    subject: "Subject",
                    body: "To Ada Smith",
                    blocks: [],
                    fallbackKeys: [],
                    toEmail: "ada@mit.edu",
                    variables: []
                };
            }
            throw new Error("unexpected url: " + url);
        };

        await sb.refreshComposeTemplatePreview();

        assert.equal(called, true);
        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("Ada Smith"));
    });

    it("random sample uses preview random-expert endpoint", async () => {
        const sb = createSandbox([customTextRow("Dear ${expertName}")]);
        const calls = [];
        sb.api = async (url, options) => {
            calls.push(url);
            if (url === "/api/qa/preview/random-expert") {
                return {
                    expert: {
                        orcidId: "0000-0001",
                        displayName: "Ada Smith",
                        email: "ada@mit.edu",
                        indexLevel: "CANDIDATE"
                    },
                    matchCount: 1,
                    totalCount: 10,
                    error: null
                };
            }
            if (url === "/api/compose-templates/preview-draft") {
                return {
                    subject: "Subject",
                    body: "Dear Ada Smith",
                    blocks: [],
                    fallbackKeys: [],
                    toEmail: "ada@mit.edu",
                    variables: []
                };
            }
            throw new Error("unexpected url: " + url);
        };
        sb.showStatus = () => {};

        await sb.randomComposeTemplatePreviewExpert();

        assert.ok(calls.includes("/api/qa/preview/random-expert"));
        assert.equal(sb.__store.get("composeTemplatePreviewExpertInput").value, "Ada Smith <ada@mit.edu>");
    });
});
