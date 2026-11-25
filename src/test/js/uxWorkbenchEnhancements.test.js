"use strict";

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const workbenchSource = fs.readFileSync(path.join(ROOT, "trust-reply-workbench.js"), "utf-8");

function composePayload() {
    return {
        frame: {
            selection: { salutationSnippetId: null, greetingSnippetId: null, ackSnippetId: null, closingSnippetId: null },
            version: "v1",
            salutation: "Dear Professor,",
            greeting: "",
            acknowledgement: "",
            closing: "Best regards,\nTalent Team"
        },
        bodyParagraphs: [
            { text: "Standard overview paragraph.", renderMode: "COMPOSE" },
            { text: "Official relocation support lasts three years.", renderMode: "VERBATIM" }
        ],
        usedFacts: [
            { factCode: "KB-FUND-034", title: "资助与经费说明", renderMode: "VERBATIM", riskLevel: "LOW", status: "APPROVED", origin: "MODEL" }
        ],
        unaddressed: [],
        corpusFingerprint: "fp-123"
    };
}

class HostStub {
    constructor() {
        this._innerHTML = "";
        this.listeners = new Map();
        this.roles = new Map();
    }
    set innerHTML(val) {
        this._innerHTML = String(val);
    }
    get innerHTML() {
        return this._innerHTML;
    }
    addEventListener(type, fn) {
        const list = this.listeners.get(type) || [];
        list.push(fn);
        this.listeners.set(type, list);
    }
    removeEventListener(type, fn) {
        this.listeners.set(type, (this.listeners.get(type) || []).filter((item) => item !== fn));
    }
    dispatch(type, event) {
        for (const fn of this.listeners.get(type) || []) fn(event);
    }
    listenerCount() {
        let total = 0;
        for (const list of this.listeners.values()) total += list.length;
        return total;
    }
    querySelector(selector) {
        const match = /data-role="([^"]+)"/.exec(selector);
        if (match && this.roles.has(match[1])) return this.roles.get(match[1]);
        return null;
    }
}

function flush() {
    return new Promise((r) => setImmediate(() => setImmediate(() => setImmediate(r))));
}

describe("Task 2: TrustReplyWorkbench Verbatim Isolation, Fact Picker & Copy Feedback", () => {
    it("isolates .trust-reply-para-tag from edited VERBATIM paragraph text in real DOM clones", async () => {
        const window = {
            document: { querySelectorAll: () => [], querySelector: () => null },
            fetch: async (url) => {
                if (String(url).endsWith("/api/rag-reply/compose")) {
                    return { ok: true, status: 200, json: async () => composePayload() };
                }
                return { ok: true, status: 200, json: async () => [] };
            },
            confirm: () => true,
            AbortController,
            navigator: { clipboard: { writeText: async () => {} } },
            setTimeout,
            clearTimeout
        };
        vm.runInNewContext(workbenchSource, { window, console, setTimeout, clearTimeout, AbortController });

        const rendered = window.TrustReplyWorkbench.renderParagraph({
            kind: "body",
            renderMode: "VERBATIM",
            text: "Official relocation support lasts three years.",
            index: 1,
            editable: true
        });
        assert.ok(rendered.includes('contenteditable="false"'), "tag span must mark contenteditable=false");

        let completedAssembly = null;
        const host = new HostStub();
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 10 },
            contextPath: "",
            autoBootstrap: true,
            onComplete: (assembly) => { completedAssembly = assembly; }
        });
        await flush();

        // Enter edit mode
        host.dispatch("click", {
            target: { dataset: { action: "edit-body" }, closest: (s) => (s === "[data-action]" ? { dataset: { action: "edit-body" } } : null) }
        });

        // Simulate real browser DOM node where textContent includes the child .trust-reply-para-tag ("逐字")
        const realDomPara = {
            dataset: { paraIndex: "1" },
            textContent: "Official relocation support lasts five years.逐字",
            classList: { add() {} },
            cloneNode() {
                let tagRemoved = false;
                return {
                    querySelectorAll(sel) {
                        if (sel === ".trust-reply-para-tag") {
                            return [{ remove() { tagRemoved = true; } }];
                        }
                        return [];
                    },
                    get textContent() {
                        return tagRemoved
                            ? "Official relocation support lasts five years."
                            : "Official relocation support lasts five years.逐字";
                    }
                };
            },
            closest(sel) { return sel === ".trust-reply-para" ? this : null; }
        };

        host.dispatch("input", { target: realDomPara });
        host.dispatch("focusout", { target: realDomPara });
        host.dispatch("click", {
            target: { dataset: { action: "complete" }, closest: (s) => (s === "[data-action]" ? { dataset: { action: "complete" } } : null) }
        });

        assert.ok(completedAssembly, "complete should emit assembly");
        assert.ok(completedAssembly.text.includes("Official relocation support lasts five years."));
        assert.ok(!completedAssembly.text.includes("逐字"), "tag text '逐字' must never leak into adopted email text");
        instance.unmount();
    });

    it("supports Enter to add fact, lazy fact suggestions from /api/rag/facts, and copy feedback", async () => {
        const composeCalls = [];
        const window = {
            document: { querySelectorAll: () => [], querySelector: () => null },
            fetch: async (url, options) => {
                const pathStr = String(url);
                if (pathStr.endsWith("/api/rag-reply/compose")) {
                    composeCalls.push(JSON.parse(options.body));
                    return { ok: true, status: 200, json: async () => composePayload() };
                }
                if (pathStr.endsWith("/api/rag/facts")) {
                    return {
                        ok: true,
                        status: 200,
                        json: async () => ({
                            facts: [
                                { factCode: "KB-VISA-001", title: "签证与入境支持", enabled: true },
                                { factCode: "KB-COMM-044", title: "远程兼职合作形式", enabled: true }
                            ]
                        })
                    };
                }
                return { ok: true, status: 200, json: async () => [] };
            },
            confirm: () => true,
            AbortController,
            navigator: { clipboard: { writeText: async () => {} } },
            setTimeout: (fn) => { window._lastTimerFn = fn; return 77; },
            clearTimeout: () => {}
        };
        vm.runInNewContext(workbenchSource, { window, console, setTimeout: window.setTimeout, clearTimeout: window.clearTimeout, AbortController });

        const host = new HostStub();
        const suggestBox = { innerHTML: "", hidden: true };
        const addInput = {
            value: "远程",
            dataset: { role: "add-fact-input" },
            closest(sel) { return sel === '[data-role="add-fact-input"]' ? this : null; }
        };
        host.roles.set("fact-suggest", suggestBox);
        host.roles.set("add-fact-input", addInput);

        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 10 },
            contextPath: "",
            autoBootstrap: false,
            onComplete: () => {}
        });

        // Typing into add-fact-input loads /api/rag/facts and populates suggestBox without losing input focus
        host.dispatch("input", { target: addInput });
        await flush();
        assert.equal(suggestBox.hidden, false, "fact suggestion box should open for matching query");
        assert.ok(suggestBox.innerHTML.includes("KB-COMM-044"), "matching fact code should appear in suggestions");

        // Pressing Enter submits the typed/matched fact code
        addInput.value = "kb-visa-001";
        let enterPrevented = false;
        host.dispatch("keydown", {
            key: "Enter",
            target: addInput,
            preventDefault() { enterPrevented = true; }
        });
        await flush();
        assert.equal(enterPrevented, true);
        assert.equal(composeCalls.length, 1);
        assert.deepEqual(composeCalls[0].forcedFactCodes, ["KB-VISA-001"]);

        // Copy button shows feedback
        host.dispatch("click", {
            target: { dataset: { action: "copy-draft" }, closest: (s) => (s === "[data-action]" ? { dataset: { action: "copy-draft" } } : null) }
        });
        await flush();
        assert.ok(host.innerHTML.includes("已复制 ✓"), "copy button should render copied confirmation");

        instance.unmount();
        assert.equal(host.listenerCount(), 0, "unmount must unbind all listeners including keydown");
    });
});
