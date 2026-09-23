const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsSource = fs.readFileSync(path.join(staticDir, "app.js"), "utf8");
const indexHtmlSource = fs.readFileSync(path.join(staticDir, "index.html"), "utf8");

function extractFn(name) {
    const match = appJsSource.match(new RegExp(`(?:async\\s+)?function\\s+${name}\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}`));
    if (!match) throw new Error(`Could not find ${name} in app.js`);
    return match[0];
}

function makeElement(id = "") {
    const listeners = {};
    const classes = new Set();
    return {
        id, value: "", textContent: "", innerHTML: "", hidden: false, disabled: false,
        dataset: {}, attributes: {}, listeners,
        classList: {
            add: (name) => classes.add(name),
            remove: (name) => classes.delete(name),
            contains: (name) => classes.has(name),
            toggle: (name, value) => value ? classes.add(name) : classes.delete(name)
        },
        addEventListener(name, fn) { (listeners[name] ||= []).push(fn); },
        setAttribute(name, value) { this.attributes[name] = value; },
        getAttribute(name) { return this.attributes[name] ?? null; },
        removeAttribute(name) { delete this.attributes[name]; },
        focus() { this.focused = true; },
        querySelector() { return null; },
        querySelectorAll() { return []; },
        closest() { return null; }
    };
}

function createEditorSandbox() {
    const elements = new Map();
    const get = (id) => {
        if (!elements.has(id)) elements.set(id, makeElement(id));
        return elements.get(id);
    };
    const inputs = [];
    const rows = [];
    const container = get("replySnippetVariantsContainer");
    Object.defineProperty(container, "innerHTML", {
        get() { return this._html || ""; },
        set(html) {
            this._html = html;
            inputs.length = 0;
            rows.length = 0;
            const rowRx = /<div class="content-variant-row var-editor-wrap" data-variant-index="(\d+)" hidden>[\s\S]*?<textarea id="replySnippetVariant-\d+" class="content-variant-input"[^>]*>([\s\S]*?)<\/textarea>[\s\S]*?<\/div>/g;
            let match;
            while ((match = rowRx.exec(html))) {
                const index = Number(match[1]);
                const input = makeElement(`replySnippetVariant-${index}`);
                input.value = match[2].replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&").replaceAll("&quot;", '"').replaceAll("&#39;", "'");
                input.classList.add("content-variant-input");
                const row = makeElement();
                row.dataset.variantIndex = String(index);
                row.querySelector = (selector) => selector === ".content-variant-input" ? input : null;
                input.closest = (selector) => selector === ".content-variant-row" ? row : selector === ".content-variants-container" ? container : null;
                rows.push(row);
                inputs.push(input);
            }
        }
    });
    container.querySelectorAll = (selector) => selector === ".content-variant-row" ? rows : selector === ".content-variant-input" ? inputs : [];
    container.querySelector = (selector) => selector.includes(".content-variant-row:not([hidden])") ? rows.find((row) => !row.hidden)?.querySelector(".content-variant-input") || null : null;
    container.closest = () => ({ querySelector: () => null });
    const originalPanel = get("replySnippetOriginalPanel");
    const originalInput = get("replySnippetContent");
    const variableButton = makeElement();
    variableButton.dataset.varInsertTarget = "replySnippetContent";
    const sandbox = {
        state: { replySnippets: [], selectedReplySnippetId: null, selectedSubjectSnippetId: null, previewDrawer: { targetId: "replySnippetContent", targetSnippetId: null } },
        $: (selector) => get(selector.replace(/^#/, "")),
        document: {
            querySelector: (selector) => selector.includes("var-insert-target") ? variableButton : get(selector.replace(/^#/, "")),
            querySelectorAll: () => [],
            getElementById: (id) => get(id),
            addEventListener: () => {},
            createElement: () => makeElement()
        },
        closeOpenVarInsertMenus: () => {},
        refreshVariableEditors: async () => {},
        schedulePreviewDrawerRefresh: () => {},
        isPreviewDrawerOpen: () => false,
        isComposeTemplatePreviewTarget: () => false,
        refreshPreviewDrawer: async () => {},
        showStatus: (message) => { sandbox.lastStatus = message; },
        escapeHtml: (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;")
    };
    sandbox.validatePlaceholderText = (text) => {
        const valid = !String(text).includes("INVALID");
        return { valid, violations: valid ? [] : ["INVALID"] };
    };
    vm.createContext(sandbox);
    ["renderContentVariantRows", "setActiveVariant", "collectContentVariants", "captureContentVariantValues", "addContentVariantRow", "removeContentVariantRow", "validateContentVariantInputs", "closeSubjectSnippetOptions", "subjectSnippetLabel", "subjectSnippetIsEligible", "findSubjectSnippet", "subjectSnippetStatus", "collectComposeTemplateSubject", "selectSubjectSnippet", "syncSubjectSnippetInput", "updateSubjectSnippetSource", "bindSubjectSnippetEditor", "saveComposeTemplate"].forEach((name) => vm.runInContext(extractFn(name), sandbox));
    sandbox.__ = { get, container, inputs, rows, originalPanel, originalInput, variableButton };
    return sandbox;
}

describe("reply snippet single-editor variants", () => {
    it("keeps original outside variants and preserves all raw values across navigation and redraw", () => {
        const sb = createEditorSandbox();
        const { container, inputs, rows, originalInput, originalPanel } = sb.__;
        originalInput.value = "MAIN";
        sb.renderContentVariantRows(container, ["A", "B", "C"]);
        assert.equal(container.dataset.activeIndex, "0");
        assert.equal(originalPanel.hidden, false);
        assert.equal(container.hidden, true);
        assert.equal(inputs.length, 3);
        sb.setActiveVariant(container, 2);
        inputs[1].value = "B2";
        assert.equal(originalPanel.hidden, true);
        assert.equal(inputs.filter((input) => !sb.__.rows[inputs.indexOf(input)].hidden).length, 1);
        sb.setActiveVariant(container, 3);
        sb.setActiveVariant(container, 2);
        assert.equal(inputs[1].value, "B2");
        assert.deepEqual(Array.from(sb.collectContentVariants(container)), ["A", "B2", "C"]);
        sb.addContentVariantRow(container);
        assert.deepEqual(Array.from(sb.captureContentVariantValues(container)), ["A", "B2", "C", ""]);
        sb.removeContentVariantRow(container, 1);
        assert.deepEqual(Array.from(sb.captureContentVariantValues(container)), ["A", "C", ""]);
        assert.equal(originalInput.value, "MAIN");
        assert.match(indexHtmlSource, /id="replySnippetContent" name="content"|id="replySnippetContent" name="content"/);
        assert.match(indexHtmlSource, /id="replySnippetVariantsContainer" hidden/);
    });
    it("reveals and focuses the first invalid hidden version before rejecting save validation", () => {
        const sb = createEditorSandbox();
        const { container, inputs } = sb.__;
        sb.renderContentVariantRows(container, ["", "B", "C"]);
        sb.setActiveVariant(container, 3);
        assert.equal(sb.validateContentVariantInputs(container, "MAIN"), false);
        assert.equal(container.dataset.activeIndex, "1");
        assert.equal(inputs[0].focused, true);
        assert.equal(sb.__.rows.filter((row) => !row.hidden).length, 1);
        inputs[0].value = "A";
        inputs[1].value = "INVALID";
        sb.setActiveVariant(container, 3);
        assert.equal(sb.validateContentVariantInputs(container, "MAIN"), false);
        assert.equal(container.dataset.activeIndex, "2");
        assert.equal(inputs[1].focused, true);
    });
});

describe("subject snippet identity and custom fallback", () => {
    it("uses the exact ID-bearing label to distinguish same-name snippets and custom text clears the ID", () => {
        const sb = createEditorSandbox();
        sb.state.replySnippets = [
            { id: 7, name: "合作主题", content: "Topic one", enabled: true },
            { id: 8, name: "合作主题", content: "Topic two", enabled: true }
        ];
        const input = sb.__.get("composeTemplateSubject");
        const badge = sb.__.get("composeSubjectSource");
        const variableButton = makeElement();
        sb.document.querySelector = () => variableButton;
        sb.subjectSnippetDisplayLabel = (snippet) => snippet.name;
        sb.replySnippetDisplayLabel = (snippet) => snippet.name;
        sb.state.selectedSubjectSnippetId = null;
        input.value = "合作主题 · #8";
        sb.syncSubjectSnippetInput();
        assert.equal(sb.state.selectedSubjectSnippetId, 8);
        assert.equal(input.value, "Topic two");
        assert.equal(badge.textContent, "引用");
        assert.equal(variableButton.disabled, true);
        input.value = "A personal question";
        sb.syncSubjectSnippetInput();
        assert.equal(sb.state.selectedSubjectSnippetId, null);
        assert.equal(badge.textContent, "自定义");
        assert.equal(variableButton.disabled, false);
        const custom = sb.collectComposeTemplateSubject();
        assert.equal(custom.subject, "A personal question");
        assert.equal(custom.subjectSnippetId, null);
        assert.notEqual(sb.subjectSnippetLabel(sb.state.replySnippets[0]), sb.subjectSnippetLabel(sb.state.replySnippets[1]));
    });

    it("uses combobox keyboard focus and Enter to select the exact ID-bearing option without form submission", () => {
        const sb = createEditorSandbox();
        sb.state.replySnippets = [
            { id: 7, name: "合作主题", content: "Topic one", enabled: true },
            { id: 8, name: "合作主题", content: "Topic two", enabled: true }
        ];
        sb.replySnippetDisplayLabel = (snippet) => snippet.name;
        const input = sb.__.get("composeTemplateSubject");
        const list = sb.__.get("composeSubjectOptions");
        const options = [7, 8].map((id) => {
            const option = makeElement(`composeSubjectOption-${id}`);
            option.dataset.snippetId = String(id);
            return option;
        });
        list.querySelectorAll = () => options;
        sb.renderSubjectSnippetOptions = () => { list.hidden = false; };
        sb.bindSubjectSnippetEditor();
        let prevented = false;
        input.listeners.keydown[0]({ key: "ArrowDown", preventDefault() { prevented = true; } });
        assert.equal(prevented, true);
        assert.equal(input.getAttribute("aria-activedescendant"), "composeSubjectOption-7");
        prevented = false;
        input.listeners.keydown[0]({ key: "Enter", preventDefault() { prevented = true; } });
        assert.equal(prevented, true);
        assert.equal(sb.state.selectedSubjectSnippetId, 7);
        assert.equal(input.value, "Topic one");
        assert.equal(list.hidden, true);
    });

    it("retains an invalid saved reference instead of falling back to its old snapshot", () => {
        const sb = createEditorSandbox();
        sb.state.selectedSubjectSnippetId = 99;
        sb.__.get("composeTemplateSubject").value = "stale snapshot";
        const result = sb.collectComposeTemplateSubject();
        assert.equal(result.subjectSnippetId, 99);
        assert.equal(result.subject, "");
        assert.equal(result.invalid, true);
    });

    it("saves reference source text and explicit null for custom subject payloads", async () => {
        const sb = createEditorSandbox();
        const form = sb.__.get("composeTemplateForm");
        form.templateName = { value: " Template " };
        form.description = { value: "" };
        form.enabled = { checked: true };
        sb.collectComposeTemplateBlocksFromForm = () => [{ blockOrder: 0, blockType: "CUSTOM_TEXT", refId: null, customText: "Body" }];
        sb.hideComposeTemplateEditor = () => {};
        sb.loadComposeTemplates = async () => {};
        sb.state.replySnippets = [{ id: 7, name: "合作主题", content: "Topic one", enabled: true }];
        sb.state.selectedSubjectSnippetId = 7;
        sb.state.selectedComposeTemplateId = 3;
        sb.__.get("composeTemplateSubject").value = "Topic one";
        let sent = null;
        sb.api = async (_url, options) => { sent = JSON.parse(options.body); };
        await sb.saveComposeTemplate({ preventDefault() {} });
        assert.equal(sent.subject, "Topic one");
        assert.equal(sent.subjectSnippetId, 7);

        sb.state.selectedSubjectSnippetId = null;
        sb.__.get("composeTemplateSubject").value = "Personal topic";
        await sb.saveComposeTemplate({ preventDefault() {} });
        assert.equal(sent.subject, "Personal topic");
        assert.equal(sent.subjectSnippetId, null);
    });
});
