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
                querySelector: () => null
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
        composeTemplatePreviewVariables: {
            senderName: "Chen Jingjing",
            expertFamilyName: "",
            researchFields: ""
        },
        $: (sel) => store.el(sel.replace(/^#/, "")),
        $$: (sel) => sel === "#composeTemplateBlocksList .compose-template-block-row" ? blocks : [],
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;")
    };
    vm.createContext(sandbox);
    [
        "placeholderDefaultFallback",
        "renderComposeTemplateText",
        "extractComposeTemplatePlaceholders",
        "composeTemplateTextHasAllPlaceholders",
        "composeTemplatePreviewExpertLabel",
        "composeTemplatePreviewAccountLabel",
        "findComposeTemplatePreviewOption",
        "expertFamilyNameFromName",
        "collectComposeTemplateBlocksFromForm",
        "selectedComposeTemplatePreviewVariables",
        "refreshComposeTemplatePreview",
        "renderComposeTemplatePreviewHtml",
        "renderLocalComposeTemplatePreview",
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

describe("compose template local preview", () => {
    it("renders subject fallback placeholders before save", () => {
        const sb = createSandbox([customTextRow("Body")]);
        sb.renderLocalComposeTemplatePreview();

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("Professor Professor - Your Field"));
        assert.ok(!html.includes("${expertFamilyName|Professor}"));
    });

    it("renders custom text placeholders before save", () => {
        const sb = createSandbox([customTextRow("Dear ${expertFamilyName|Professor}, from ${senderName}")]);
        sb.renderLocalComposeTemplatePreview();

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("Dear Professor, from Chen Jingjing"));
    });

    it("provides default fallback copy for nullable expert placeholders", () => {
        const sb = createSandbox([]);

        assert.equal(sb.placeholderDefaultFallback("degree"), "your academic background");
        assert.equal(sb.placeholderDefaultFallback("recentWorkTitle"), "your recent research");
        assert.equal(sb.placeholderDefaultFallback("lastPublicationYear"), "recent years");
        assert.equal(sb.placeholderDefaultFallback("unsubscribeUrl"), "");
    });

    it("uses selected expert and sender account variables", () => {
        const sb = createSandbox([customTextRow("From ${senderEmail} to ${expertName}")]);
        sb.state.composeTemplatePreviewExperts = [
            { id: 7, expertName: "Ada Smith", expertEmail: "ada@mit.edu" }
        ];
        sb.state.composeTemplatePreviewAccounts = [
            { accountCode: "ops", senderEmail: "ops@example.com", senderName: "Ops Team" }
        ];
        sb.__store.el("composeTemplatePreviewExpertInput").value = "Ada Smith <ada@mit.edu>";
        sb.__store.el("composeTemplatePreviewAccountInput").value = "ops <ops@example.com>";

        sb.renderLocalComposeTemplatePreview();

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("To</span><strong>ada@mit.edu"));
        assert.ok(html.includes("From ops@example.com to Ada Smith"));
    });

    it("strict placeholder mode skips text with missing variables", () => {
        const sb = createSandbox([
            customTextRow("Visible ${senderName}"),
            customTextRow("Hidden ${researchFields}")
        ]);
        sb.__store.el("composeTemplatePreviewStrictPlaceholders").checked = true;

        sb.renderLocalComposeTemplatePreview();

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("Visible Chen Jingjing"));
        assert.ok(!html.includes("Hidden"));
        assert.ok(html.includes("已跳过 1 段"));
    });

    it("refresh uses local selected variables for existing templates", async () => {
        const sb = createSandbox([customTextRow("To ${expertName}")]);
        sb.state.selectedComposeTemplateId = 99;
        sb.state.composeTemplatePreviewExperts = [
            { id: 7, expertName: "Ada Smith", expertEmail: "ada@mit.edu" }
        ];
        sb.__store.el("composeTemplatePreviewExpertInput").value = "Ada Smith <ada@mit.edu>";
        sb.api = async () => {
            throw new Error("server preview should not be called");
        };

        await sb.refreshComposeTemplatePreview();

        const html = sb.__store.get("composeTemplatePreviewPanel").innerHTML;
        assert.ok(html.includes("Ada Smith"));
        assert.ok(!html.includes("${expertName}"));
    });
});
