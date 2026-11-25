"use strict";

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");

function readStatic(file) {
    return fs.readFileSync(path.join(ROOT, file), "utf-8");
}

function extractFunction(source, name) {
    const startRe = new RegExp(`(?:async\\s+)?function\\s+${name}\\s*\\(`);
    const match = startRe.exec(source);
    assert.ok(match, `expected function ${name} in app.js`);
    let idx = source.indexOf("{", match.index);
    let depth = 1;
    for (idx += 1; idx < source.length && depth > 0; idx += 1) {
        if (source[idx] === "{") depth += 1;
        else if (source[idx] === "}") depth -= 1;
    }
    return source.slice(match.index, idx);
}

describe("Task 1: Overlay Escape Stack, Dirty Guard, Toast Stack & Theme Hierarchy", () => {
    it("Escape closes previewDrawer first without closing underlying composeTemplateModal", () => {
        const appSource = readStatic("app.js");
        let previewCollapsed = 0;
        let composeClosed = 0;
        const modals = {
            previewDrawer: { hidden: false },
            composeTemplateModal: { hidden: false },
            replySnippetModal: { hidden: true },
            qaRuleModal: { hidden: true },
            accountModal: { hidden: true },
            suppressionModal: { hidden: true },
            actionDialog: { open: false }
        };
        const sandbox = {
            $: (sel) => modals[sel.replace(/^#/, "")] || null,
            $$: () => [],
            document: {
                getElementById: (id) => modals[id] || null,
                querySelector: () => null
            },
            collapsePreviewDrawer: () => {
                previewCollapsed += 1;
                modals.previewDrawer.hidden = true;
            },
            requestCloseEditorModal: (modalId) => {
                if (modalId === "composeTemplateModal") composeClosed += 1;
            },
            closeOpenVarInsertMenus: () => {},
            closeSubjectSnippetOptions: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFunction(appSource, "handleGlobalEditorEscape"), sandbox);

        let prevented = 0;
        sandbox.handleGlobalEditorEscape({
            key: "Escape",
            defaultPrevented: false,
            preventDefault() { prevented += 1; }
        });
        assert.equal(previewCollapsed, 1, "first Escape must collapse previewDrawer");
        assert.equal(composeClosed, 0, "first Escape must NOT close underlying composeTemplateModal");
        assert.equal(prevented, 1);

        sandbox.handleGlobalEditorEscape({
            key: "Escape",
            defaultPrevented: false,
            preventDefault() { prevented += 1; }
        });
        assert.equal(composeClosed, 1, "second Escape may request closing composeTemplateModal");
    });

    it("requestCloseEditorModal closes clean modal immediately and confirms when dirty", async () => {
        const appSource = readStatic("app.js");
        let closed = 0;
        let confirmCalls = 0;
        let confirmResult = null;
        let dirty = false;
        const sandbox = {
            isModalFormDirty: () => dirty,
            openActionDialog: async (type) => {
                assert.equal(type, "confirm");
                confirmCalls += 1;
                return confirmResult;
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFunction(appSource, "requestCloseEditorModal"), sandbox);

        await sandbox.requestCloseEditorModal("qaRuleModal", () => { closed += 1; });
        assert.equal(closed, 1, "clean modal closes without confirm");
        assert.equal(confirmCalls, 0);

        dirty = true;
        confirmResult = null;
        await sandbox.requestCloseEditorModal("qaRuleModal", () => { closed += 1; });
        assert.equal(confirmCalls, 1, "dirty modal asks confirmation");
        assert.equal(closed, 1, "cancelling confirm keeps modal open");

        confirmResult = { ok: true };
        await sandbox.requestCloseEditorModal("qaRuleModal", () => { closed += 1; });
        assert.equal(confirmCalls, 2);
        assert.equal(closed, 2, "confirming closes dirty modal");
    });

    it("showStatus pushes deduplicated non-blocking toasts into #consoleToastStack while updating #statusBar", () => {
        const appSource = readStatic("app.js");
        const children = [];
        const stack = {
            id: "consoleToastStack",
            children,
            appendChild(node) { children.push(node); },
            querySelectorAll() { return children; }
        };
        const statusBar = { textContent: "", className: "", hidden: true };
        const sandbox = {
            $: (sel) => (sel === "#statusBar" ? statusBar : sel === "#consoleToastStack" ? stack : null),
            document: {
                body: {
                    classList: { contains: (cls) => cls === "console-ui" },
                    appendChild() {}
                },
                getElementById: (id) => (id === "consoleToastStack" ? stack : null),
                createElement(tag) {
                    return {
                        tagName: tag.toUpperCase(),
                        className: "",
                        dataset: {},
                        attributes: {},
                        children: [],
                        setAttribute(k, v) { this.attributes[k] = v; },
                        appendChild(c) { this.children.push(c); },
                        addEventListener() {},
                        remove() {
                            const idx = children.indexOf(this);
                            if (idx >= 0) children.splice(idx, 1);
                        }
                    };
                }
            },
            Date,
            setTimeout: () => 1,
            clearTimeout: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFunction(appSource, "pushConsoleToast"), sandbox);
        vm.runInContext(extractFunction(appSource, "showStatus"), sandbox);

        sandbox.showStatus("QA 事实已保存", "ok");
        sandbox.showStatus("QA 事实已保存", "ok");
        assert.equal(statusBar.textContent, "QA 事实已保存");
        assert.equal(children.length, 1, "duplicate toast in same burst must be deduplicated");
        assert.match(children[0].className, /console-toast/);
    });

    it("ACTION_DIALOG_SCHEMAS includes insert-link with safe protocol validation", () => {
        const appSource = readStatic("app.js");
        const start = appSource.indexOf("const ACTION_DIALOG_SCHEMAS = {");
        assert.ok(start >= 0);
        const end = appSource.indexOf("\n};", start);
        const block = appSource.slice(start, end + 3);
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(`${block}; this.schemas = ACTION_DIALOG_SCHEMAS;`, sandbox);
        const schema = sandbox.schemas["insert-link"];
        assert.ok(schema, "insert-link schema must exist");
        assert.equal(schema.validate({ url: "https://example.org/docs" }), null);
        assert.equal(schema.validate({ url: "mailto:expert@example.org" }), null);
        assert.equal(schema.validate({ url: "www.example.org" }), null);
        assert.match(schema.validate({ url: "javascript:alert(1)" }) || "", /协议|链接|URL/);
    });

    it("fixes z-index inversion in console-shell.css and differentiates dark secondary buttons in console-theme.css", () => {
        const shellCss = readStatic("console-shell.css");
        const themeCss = readStatic("console-theme.css");
        assert.match(shellCss, /\.console-ui\s+\.modal-shell|\.modal-shell/, "modal-shell z-index override must exist");
        assert.match(shellCss, /\.preview-drawer-shell/, "preview-drawer-shell z-index override must exist");
        assert.match(shellCss, /\.console-toast-stack/, "toast stack style must exist");
        assert.match(themeCss, /\.button\.secondary/, "dark theme must explicitly style .button.secondary distinct from neutral .button");
    });
});
