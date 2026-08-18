const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const workbenchPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const workbenchSource = fs.readFileSync(workbenchPath, "utf-8");
const appPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appSource = fs.readFileSync(appPath, "utf-8");

class FakeElement {
    constructor(ownerDocument) {
        this.ownerDocument = ownerDocument;
        this._innerHTML = "";
        this.hidden = false;
        this.listeners = new Map();
        this.attributes = new Map();
        this.dataset = {};
    }

    set innerHTML(value) {
        this._innerHTML = String(value);
    }
    get innerHTML() { return this._innerHTML; }
    setAttribute(name, value) { this.attributes.set(name, String(value)); }
    getAttribute(name) { return this.attributes.get(name) || null; }
    addEventListener(type, listener) {
        const list = this.listeners.get(type) || [];
        list.push(listener);
        this.listeners.set(type, list);
    }
    removeEventListener(type, listener) {
        this.listeners.set(type, (this.listeners.get(type) || []).filter((item) => item !== listener));
    }
    dispatchEvent(type, target) {
        for (const listener of this.listeners.get(type) || []) listener({ target });
    }
    contains() { return true; }
    querySelector() { return null; }
    querySelectorAll(selector) {
        if (selector === "[data-role]") {
            return [...this.innerHTML.matchAll(/data-role="([^"]+)"/g)].map((match) => ({ dataset: { role: match[1] } }));
        }
        return [];
    }
}

class FakeDocument {
    constructor() { this.activeElement = null; }
    createElement() { return new FakeElement(this); }
}

function settle() {
    return new Promise((resolve) => setImmediate(() => setImmediate(resolve)));
}

function click(host, action, requestKey) {
    host.dispatchEvent("click", {
        dataset: { action, requestKey },
        closest: () => ({ dataset: { action, requestKey } })
    });
}

function jsonResponse(body) {
    return { ok: true, status: 200, json: async () => body };
}

function createSandbox(fetchImpl) {
    const document = new FakeDocument();
    const window = {
        document,
        fetch: fetchImpl,
        confirm: () => true,
        crypto: { randomUUID: () => "00000000-0000-4000-8000-000000000001" },
        AbortController,
        TextDecoder,
        TextEncoder,
        setTimeout,
        clearTimeout
    };
    const sandbox = { window, document, console, setTimeout, clearTimeout, AbortController, TextDecoder, TextEncoder };
    vm.createContext(sandbox);
    vm.runInContext(workbenchSource, sandbox);
    return { sandbox, window, document };
}

function autoPreviewBootstrap(sourceId, coverageItems) {
    return {
        source: { sourceType: "LIVE_INBOUND", sourceId },
        sourceVersion: `LIVE_INBOUND-${sourceId}-v1`,
        inboundSubject: "subject",
        inboundText: "body",
        expertName: "Expert",
        expertEmail: "expert@example.com",
        llmEnabled: true,
        availableModels: ["DEEPSEEK_V4_FLASH"],
        defaultModel: "DEEPSEEK_V4_FLASH",
        suggestedFactIds: [1],
        canonicalFactIds: [1],
        rulesByCategory: [{ ruleId: 1, displayName: "Fact" }],
        requestCoverage: coverageItems,
        requestFactSelections: [],
        savedState: null
    };
}

function coverageItem(sourceId, index, status) {
    return {
        index,
        requestKey: `LIVE_INBOUND-${sourceId}-request${index}`,
        requestText: `Question ${index + 1}`,
        status,
        factRuleIds: status === "UNSUPPORTED" ? [] : [1],
        allowedHandlings: status === "UNSUPPORTED"
            ? ["ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"]
            : ["ANSWER_WITH_EVIDENCE", "OMIT"],
        recommendedHandling: status === "UNSUPPORTED" ? "ACKNOWLEDGE_PENDING" : "ANSWER_WITH_EVIDENCE"
    };
}

// Extracts a top-level function definition from app.js so the read-only zone
// renderer can be executed against a fake DOM (I-3 contract test).
function extractFunction(source, name) {
    const marker = `function ${name}(`;
    const start = source.indexOf(marker);
    assert.notStrictEqual(start, -1, `app.js must define ${name}`);
    const open = source.indexOf("{", start);
    let depth = 0;
    let end = -1;
    for (let i = open; i < source.length; i += 1) {
        const ch = source[i];
        if (ch === "{") depth += 1;
        else if (ch === "}") {
            depth -= 1;
            if (depth === 0) { end = i + 1; break; }
        }
    }
    assert.notStrictEqual(end, -1, `could not extract ${name} from app.js`);
    return source.slice(start, end);
}

class GateListElement {
    constructor() { this._innerHTML = ""; }
    get innerHTML() { return this._innerHTML; }
    insertAdjacentHTML(position, html) {
        if (position === "beforeend") this._innerHTML += String(html);
    }
}

class PreviewHostElement {
    constructor() {
        this._innerHTML = "";
        this._gateList = new GateListElement();
    }
    get innerHTML() { return this._innerHTML; }
    set innerHTML(value) { this._innerHTML = String(value); }
    insertAdjacentHTML(position, html) {
        if (position === "beforeend") this._innerHTML += String(html);
    }
    querySelector(selector) {
        if (selector === ".trust-reply-gate-list" && /<ul class="trust-reply-gate-list">/.test(this._innerHTML)) {
            return this._gateList;
        }
        return null;
    }
}

describe("AUTO_PREVIEW workbench host", () => {
    it("mounts read-only and issues exactly one /bootstrap request", async () => {
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            return Promise.resolve(jsonResponse(autoPreviewBootstrap(42, [coverageItem(42, 0, "GROUNDED")])));
        });
        const host = new FakeElement(window.document);
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "AUTO_PREVIEW",
            source: { sourceType: "LIVE_INBOUND", sourceId: 42 },
            contextPath: ""
        });
        await settle();
        assert.strictEqual(calls.length, 1, "AUTO_PREVIEW must issue only the bootstrap request");
        assert.ok(calls[0].url.endsWith("/api/trust-reply/workbench/bootstrap"), calls[0].url);
        assert.ok(String(host.getAttribute("class") || "").includes("trust-reply-readonly"));
        assert.ok(host.innerHTML.includes("只读预览：此处不生成、不采用、不发送"));
        assert.ok(host.innerHTML.includes('<ul class="trust-reply-gate-list"></ul>'));
        instance.unmount();
        await settle();
        assert.strictEqual(host.innerHTML, "");
    });

    it("blocks write paths at the requestJson gate and registers no listeners", async () => {
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            return Promise.resolve(jsonResponse(autoPreviewBootstrap(43, [coverageItem(43, 0, "UNSUPPORTED")])));
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "AUTO_PREVIEW",
            source: { sourceType: "LIVE_INBOUND", sourceId: 43 },
            contextPath: ""
        });
        await settle();
        // The UNSUPPORTED item auto-triggers the translation request; the
        // fail-closed gate must swallow it before any fetch is issued.
        click(host, "assemble");
        click(host, "resolve-item");
        click(host, "adjust-item");
        await settle();
        assert.strictEqual(calls.length, 1, "assemble/generations/state/translate must never fetch");
        assert.ok(calls[0].url.endsWith("/bootstrap"));
    });

    it("renders the body even when wouldBeBlockedBy is non-empty (I-3)", () => {
        const renderFn = extractFunction(appSource, "renderAutoPreviewIntoHost");
        const sandbox = {
            console,
            escapeHtml: (value) => String(value ?? ""),
            translatableBody: (text) => `<div class="translatable-body-block">${String(text ?? "")}</div>`
        };
        vm.createContext(sandbox);
        vm.runInContext(renderFn, sandbox);

        const gated = new PreviewHostElement();
        gated.innerHTML = '<ul class="trust-reply-gate-list"></ul>';
        const gatedList = gated.querySelector(".trust-reply-gate-list");
        sandbox.renderAutoPreviewIntoHost(gated, {
            replyBody: "完整回复正文",
            replySubject: "Re: 主题",
            reason: "QA 缺口",
            wouldBeBlockedBy: ["AUTO_REPLY_DISABLED", "RECIPIENT_UNSUBSCRIBED"]
        });
        const gateCount = (gatedList.innerHTML.match(/class="trust-reply-gate-item"/g) || []).length;
        assert.strictEqual(gateCount, 2, "gate item count must equal wouldBeBlockedBy length");
        assert.ok(gated.innerHTML.includes("完整回复正文"), "body must render alongside non-empty gates");

        const unblocked = new PreviewHostElement();
        unblocked.innerHTML = '<ul class="trust-reply-gate-list"></ul>';
        const unblockedList = unblocked.querySelector(".trust-reply-gate-list");
        sandbox.renderAutoPreviewIntoHost(unblocked, {
            replyBody: "无闸门正文",
            replySubject: "",
            wouldBeBlockedBy: []
        });
        assert.strictEqual(unblockedList.innerHTML, "", "empty gate list stays empty");
        assert.ok(unblocked.innerHTML.includes("无闸门正文"));

        const noBody = new PreviewHostElement();
        noBody.innerHTML = '<ul class="trust-reply-gate-list"></ul>';
        sandbox.renderAutoPreviewIntoHost(noBody, {
            replyBody: null,
            previewKind: "QA_GAP",
            reason: "当前来信存在 QA 缺口，需要人工确认",
            wouldBeBlockedBy: []
        });
        assert.ok(noBody.innerHTML.includes("当前来信存在 QA 缺口，需要人工确认"), "reason must fill the body zone");
        assert.ok(!noBody.innerHTML.includes("暂无自动回复正文"));
    });

    it("mounts without onComplete (T1 relaxation) and never completes", async () => {
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            return Promise.resolve(jsonResponse(autoPreviewBootstrap(7, [coverageItem(7, 0, "GROUNDED")])));
        });
        const host = new FakeElement(window.document);
        let instance;
        assert.doesNotThrow(() => {
            instance = window.TrustReplyWorkbench.mount(host, {
                mode: "AUTO_PREVIEW",
                source: { sourceType: "LIVE_INBOUND", sourceId: 7 },
                contextPath: ""
            });
        });
        await settle();
        assert.strictEqual(calls.length, 1);
        assert.ok(host.innerHTML.includes("只读预览"));
        assert.doesNotThrow(() => instance.unmount());
    });
});
