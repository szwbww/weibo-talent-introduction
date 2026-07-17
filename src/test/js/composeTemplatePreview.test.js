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
                hidden: false,
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
            selectedComposeTemplateId: null,
            previewDrawer: {
                targetId: "composeTemplate",
                variantIndex: 0,
                variantPoolSize: 1
            }
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
        replySnippetTypeLabels: {
            greeting: "问候"
        },
        isPreviewDrawerOpen: () => true,
        isComposeTemplatePreviewTarget: () => true,
        updatePreviewCoverage: () => {},
        updatePreviewVariantSwitcher: () => {},
        api: async () => ({
            subject: "Professor Professor - Your Field",
            body: "Dear Professor, from Chen Jingjing",
            blocks: [{ blockOrder: 0, blockType: "CUSTOM_TEXT", included: true }],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variantPoolSize: 1,
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
        "composeTemplateBlockRowHtml",
        "collectComposeTemplateBlocksFromForm",
        "collectComposeTemplatePreviewContext",
        "collectComposeTemplatePreviewSampleText",
        "refreshComposeTemplatePreview",
        "renderComposeTemplatePreviewHtml",
        "renderComposeTemplatePreviewVariableRows",
        "renderComposeTemplatePreviewInDrawer",
        "renderServerComposeTemplatePreview",
        "randomComposeTemplatePreviewExpert",
        "updatePreviewVariantSwitcher",
        "shouldDockPreviewInComposeTemplate"
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
    it("docks compose template preview inside the editor", () => {
        const sb = createSandbox([]);

        assert.equal(sb.shouldDockPreviewInComposeTemplate("composeTemplate", false), true);
        assert.equal(sb.shouldDockPreviewInComposeTemplate("qaRuleReplyBody", false), false);
        assert.equal(sb.shouldDockPreviewInComposeTemplate("composeTemplate", true), false);
    });

    it("renders server preview response in drawer", () => {
        const sb = createSandbox([customTextRow("Dear ${expertFamilyName|Professor}, from ${senderName}")]);
        sb.renderComposeTemplatePreviewInDrawer({
            subject: "Professor Professor - Your Field",
            body: "Dear Professor, from Chen Jingjing",
            blocks: [{ blockOrder: 0, blockType: "CUSTOM_TEXT", included: true }],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variantPoolSize: 1,
            variables: []
        });

        assert.equal(sb.__store.get("previewMailTo").textContent, "ada@mit.edu");
        assert.equal(sb.__store.get("previewMailSubject").textContent, "Professor Professor - Your Field");
        assert.equal(sb.__store.get("previewMailBody").textContent, "Dear Professor, from Chen Jingjing");
    });

    it("strict placeholder mode shows skipped blocks from server", () => {
        const sb = createSandbox([
            customTextRow("Visible ${senderName}"),
            customTextRow("Hidden ${researchFields}")
        ]);
        sb.renderComposeTemplatePreviewInDrawer({
            subject: "Subject",
            body: "Visible Chen Jingjing",
            blocks: [
                { blockOrder: 0, blockType: "CUSTOM_TEXT", included: true },
                { blockOrder: 1, blockType: "CUSTOM_TEXT", included: false, skipReason: "存在未满足占位符" }
            ],
            fallbackKeys: ["researchFields"],
            toEmail: "expert@example.com",
            variantPoolSize: 1,
            variables: []
        });

        assert.equal(sb.__store.get("previewMailBody").textContent, "Visible Chen Jingjing");
        assert.ok(sb.__store.get("previewComposeSkipped").textContent.includes("已跳过 1 段"));
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
                    variantPoolSize: 1,
                    variables: []
                };
            }
            throw new Error("unexpected url: " + url);
        };

        await sb.refreshComposeTemplatePreview();

        assert.equal(called, true);
        assert.equal(sb.__store.get("previewMailBody").textContent, "To Ada Smith");
    });

    it("random sample uses preview random-expert endpoint", async () => {
        const sb = createSandbox([customTextRow("Dear ${expertName}")]);
        const calls = [];
        let previewPayload = null;
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
                previewPayload = JSON.parse(options.body);
                return {
                    subject: "Subject",
                    body: "Dear Ada Smith",
                    blocks: [],
                    fallbackKeys: [],
                    toEmail: "ada@mit.edu",
                    variantPoolSize: 1,
                    variables: []
                };
            }
            throw new Error("unexpected url: " + url);
        };
        sb.showStatus = () => {};

        await sb.randomComposeTemplatePreviewExpert();

        assert.ok(calls.includes("/api/qa/preview/random-expert"));
        assert.equal(sb.__store.get("previewComposeExpertInput").value, "Ada Smith <ada@mit.edu>");
        assert.equal(previewPayload.orcidId, "0000-0001");
        assert.equal(previewPayload.expertEmail, "ada@mit.edu");
    });

    it("compose block editor omits QA_RULE option and defaults to CUSTOM_TEXT", () => {
        const sb = createSandbox([]);
        const defaultRow = sb.composeTemplateBlockRowHtml(0, {});
        assert.ok(!defaultRow.includes('<option value="QA_RULE">'));
        assert.ok(defaultRow.includes('<option value="CUSTOM_TEXT" selected'));
        assert.ok(defaultRow.includes('data-field="customText"'));

        const blocks = sb.collectComposeTemplateBlocksFromForm();
        assert.equal(blocks.length, 0);
    });

    it("compose block row keeps QA sample text branch for legacy preview", () => {
        const sb = createSandbox([]);
        sb.state.qaRules = [{ id: 1, replyBody: "Legacy QA body" }];
        const row = {
            querySelector(selector) {
                if (selector === '[data-field="blockType"]') return { value: "QA_RULE" };
                if (selector === '[data-field="refId"]') return { value: "1" };
                return null;
            }
        };
        sb.$$ = () => [row];
        assert.equal(
            sb.collectComposeTemplatePreviewSampleText(),
            'Professor ${expertFamilyName|Professor} - ${researchFields|Your Field}\nLegacy QA body'
        );
    });
});
