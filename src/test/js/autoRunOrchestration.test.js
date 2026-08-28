const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const componentPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const source = fs.readFileSync(componentPath, "utf-8");
const styles = fs.readFileSync(stylesPath, "utf-8");

class FakeElement {
    constructor(ownerDocument) {
        this.ownerDocument = ownerDocument;
        this._innerHTML = "";
        this.hidden = false;
        this.listeners = new Map();
        this.attributes = new Map();
        this.dataset = {};
        this.innerHTMLWriteCount = 0;
    }

    set innerHTML(value) {
        this.innerHTMLWriteCount += 1;
        this._innerHTML = String(value);
        if (this.ownerDocument.activeElement?.ownerHost === this) this.ownerDocument.activeElement = null;
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
    querySelectorAll(selector) {
        if (selector === "[data-role]") {
            return [...this._innerHTML.matchAll(/data-role="([^"]+)"/g)].map((match) => {
                const element = new FakeElement(this.ownerDocument);
                element.dataset.role = match[1];
                const before = this._innerHTML.slice(0, match.index);
                const tagStart = before.lastIndexOf("<");
                const tagEnd = before.lastIndexOf(">");
                if (tagStart > tagEnd) {
                    const keyMatch = before.slice(tagStart).match(/data-request-key="([^"]+)"/);
                    if (keyMatch) element.dataset.requestKey = keyMatch[1];
                }
                return element;
            });
        }
        return [];
    }
    querySelector() { return null; }
}

class FakeDocument {
    constructor() { this.activeElement = null; }
    createElement() { return new FakeElement(this); }
}

function jsonResponse(body) {
    return { ok: true, status: 200, json: async () => body };
}

function sseResponse(event, data) {
    const frame = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    let consumed = false;
    const reader = {
        async read() {
            if (consumed) return { done: true, value: undefined };
            consumed = true;
            return { done: false, value: new TextEncoder().encode(frame) };
        },
        async cancel() { }
    };
    return { ok: true, status: 200, body: { getReader: () => reader } };
}

function settle() {
    return new Promise((resolve) => setImmediate(() => setImmediate(resolve)));
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
    return { promise, resolve, reject };
}

function click(host, action, requestKey) {
    host.dispatchEvent("click", {
        dataset: { action, requestKey },
        closest: () => ({ dataset: { action, requestKey } })
    });
}

// Backend-realistic coverage: recommendedHandling mirrors the service contract
// (UNSUPPORTED -> ANSWER_FROM_OPERATOR_INPUT) and only UNSUPPORTED items carry
// a suggestedInstruction (I-2).
function coverageItem(sourceType, sourceId, index, status) {
    const requestKey = `${sourceType}-${sourceId}-request${index + 1}`;
    return {
        index,
        requestKey,
        requestText: `Question ${index + 1}`,
        status,
        factRuleIds: status === "UNSUPPORTED" ? [] : [1],
        allowedHandlings: status === "UNSUPPORTED"
            ? ["ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"]
            : status === "PARTIAL"
                ? ["ANSWER_SUPPORTED_PART", "ACKNOWLEDGE_PENDING", "OMIT"]
                : ["ANSWER_WITH_EVIDENCE", "OMIT"],
        recommendedHandling: status === "UNSUPPORTED"
            ? "ANSWER_FROM_OPERATOR_INPUT"
            : status === "PARTIAL"
                ? "ANSWER_SUPPORTED_PART"
                : "ANSWER_WITH_EVIDENCE",
        suggestedInstruction: status === "UNSUPPORTED"
            ? "这一条我们库里没有确认口径。请按真人对接人的方式回答：先明说没有确认答案，最后交出下一步但不承诺具体时间。不要出现数字、链接或时间承诺。"
            : undefined
    };
}

function bootstrap(sourceType, sourceId, coverageItems) {
    return {
        source: { sourceType, sourceId },
        sourceVersion: `${sourceType}-${sourceId}-v1`,
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
        draftReadiness: "READY",
        contextWarnings: [],
        evidenceSetVersion: `${sourceType}-${sourceId}-e1`,
        requestFactSelections: coverageItems.map((item) => ({
            requestKey: item.requestKey,
            factRuleIds: [...(item.factRuleIds || [])]
        }))
    };
}

function itemVersion(requestKey, current, handling) {
    return {
        versionId: `${requestKey}-v1`,
        requestKey,
        handling,
        answerText: "answer",
        claims: [],
        model: "DEEPSEEK_V4_FLASH",
        generationKind: "AI_GENERATED",
        evidenceSetVersion: current.evidenceSetVersion,
        sourceVersion: current.sourceVersion,
        operatorInstructionHash: "hash",
        operatorInstruction: handling === "ANSWER_FROM_OPERATOR_INPUT" ? "machine instruction" : ""
    };
}

function assembleResponse(current, itemVersions) {
    return jsonResponse({
        source: current.source,
        sourceVersion: current.sourceVersion,
        evidenceSetVersion: current.evidenceSetVersion,
        rawDraftText: "assembled draft",
        renderedDraftText: "assembled draft with variables replaced",
        draftHash: "hash",
        canonicalFactIds: [1],
        itemVersions,
        requestFactSelections: current.requestCoverage.map((item) => ({
            requestKey: item.requestKey,
            factRuleIds: [...(item.factRuleIds || [])]
        }))
    });
}

function stateResponse(stateVersion) {
    return jsonResponse({ status: "SAVED", stateVersion, lockedItems: [] });
}

// Fresh LIVE instance bootstrapped with a saved state (R-1 provenance restore).
function mountRestored(sourceId, savedState) {
    const sourceType = "LIVE_INBOUND";
    const current = bootstrap(sourceType, sourceId, [coverageItem(sourceType, sourceId, 0, "UNSUPPORTED")]);
    current.savedState = savedState;
    const statePayloads = [];
    const { window } = createSandbox((url, options) => {
        if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
        if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
        if (url.includes("/state")) {
            statePayloads.push(JSON.parse(options.body));
            return Promise.resolve(stateResponse(statePayloads.length));
        }
        throw new Error(`unexpected request: ${url}`);
    });
    const host = new FakeElement(window.document);
    window.TrustReplyWorkbench.mount(host, {
        mode: "LIVE",
        source: current.source,
        contextPath: "",
        onComplete: async () => {}
    });
    return { host, statePayloads };
}

// LIVE one-click run with a controllable existing auto-reply preview response.
function liveAutoRunFixture(sourceId, previewImpl) {
    const current = bootstrap("LIVE_INBOUND", sourceId, [coverageItem("LIVE_INBOUND", sourceId, 0, "UNSUPPORTED")]);
    const calls = [];
    const { window } = createSandbox((url, options) => {
        calls.push({ url, options });
        if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
        if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
        if (url.includes("/generations/stream")) {
            const payload = JSON.parse(options.body);
            const version = itemVersion(payload.requestKey, current, payload.handling);
            return Promise.resolve(sseResponse("result", {
                source: current.source,
                sourceVersion: current.sourceVersion,
                evidenceSetVersion: current.evidenceSetVersion,
                version
            }));
        }
        if (url.includes("/state")) return Promise.resolve(stateResponse(1));
        if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, []));
        if (url.includes("/auto-reply-preview")) return Promise.resolve(previewImpl());
        throw new Error(`unexpected request: ${url}`);
    });
    const host = new FakeElement(window.document);
    window.TrustReplyWorkbench.mount(host, {
        mode: "LIVE",
        source: current.source,
        contextPath: "",
        onComplete: async () => {}
    });
    return { calls, host };
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
    vm.runInContext(source, sandbox);
    return { sandbox, window, document };
}

describe("one-click orchestration (auto-run / auto-reset)", () => {
    it("exposes the aggregate actions and the machine-fill badge without dead markup", () => {
        assert.match(source, /data-action="auto-run"/);
        assert.match(source, /data-action="auto-reset"/);
        assert.match(source, /一键预判/);
        // I-3: the badge must never be rendered disabled or masked.
        for (const line of source.split("\n")) {
            if (line.includes("trust-reply-autofilled")) {
                assert.doesNotMatch(line, /disabled/);
            }
        }
        // I-2: the suggested wording lives on the server, never in the JS.
        assert.doesNotMatch(source, /希望如何回答|请按真人|机器代填说明/);
        // S-1: the three style blocks are present verbatim.
        assert.match(styles, /\.trust-reply-autorun \{/);
        assert.match(styles, /\.trust-reply-autorun-hint \{/);
        assert.match(styles, /\.trust-reply-autofilled \{/);
        assert.match(styles, /border-left: 2px solid var\(--primary\);/);
    });

    it("fills every UNSUPPORTED item via ADJUST_ITEM with ANSWER_FROM_OPERATOR_INPUT and a non-empty instruction, then assembles once", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 601;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [unsupported]);
        const calls = [];
        const streamPayloads = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamPayloads.push(payload);
                const version = itemVersion(payload.requestKey, current, payload.handling || "ANSWER_FROM_OPERATOR_INPUT");
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [unsupported]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();

        assert.strictEqual(streamPayloads.length, 1);
        assert.strictEqual(streamPayloads[0].operation, "ADJUST_ITEM");
        assert.strictEqual(streamPayloads[0].requestKey, unsupported.requestKey);
        assert.strictEqual(streamPayloads[0].handling, "ANSWER_FROM_OPERATOR_INPUT");
        assert.strictEqual(streamPayloads[0].operatorInstruction, unsupported.suggestedInstruction);
        assert.ok(streamPayloads[0].operatorInstruction.length > 0);
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 1);
        assert.match(host.innerHTML, /机器代填/);
        assert.match(host.innerHTML, /汇总已完成/);
        assert.match(host.innerHTML, /硬性闸门：尚未预判/);
        assert.match(host.innerHTML, /assembled draft/);
    });

    it("defaults the integration preview to the rendered draft and switches to raw via set-preview-tab (计划 03)", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 633;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [unsupported]);
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = itemVersion(payload.requestKey, current, payload.handling || "ANSWER_FROM_OPERATOR_INPUT");
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [unsupported]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();

        // I-6: after assembly the default preview tab shows the rendered draft
        // (variables replaced) — never the raw placeholder text.
        assert.match(host.innerHTML, /data-role="rendered-preview"/);
        assert.match(host.innerHTML, /assembled draft with variables replaced/);
        assert.doesNotMatch(host.innerHTML, />assembled draft</);

        // I-7: switching tabs goes through state + full render() — the click
        // only carries the dataset; no host DOM query is involved.
        host.dispatchEvent("click", {
            dataset: { action: "set-preview-tab", previewTab: "raw" },
            closest: () => ({ dataset: { action: "set-preview-tab", previewTab: "raw" } })
        });
        assert.match(host.innerHTML, /data-role="raw-preview"/);
        assert.match(host.innerHTML, />assembled draft</);
        assert.doesNotMatch(host.innerHTML, /assembled draft with variables replaced/);

        // S-1/S-2: the three tab buttons render; both legacy preview roles stay
        // reachable in the component source.
        assert.match(host.innerHTML, /data-action="set-preview-tab" data-preview-tab="rendered"/);
        assert.match(host.innerHTML, /data-action="set-preview-tab" data-preview-tab="local"/);
        assert.match(host.innerHTML, /data-action="set-preview-tab" data-preview-tab="raw"/);
        assert.match(source, /data-role="local-preview"/);
        assert.match(source, /data-role="raw-preview"/);
    });

    it("handles every coverage kind with the recommended handling and calls assemble exactly once", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 602;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const partial = coverageItem(sourceType, sourceId, 1, "PARTIAL");
        const grounded = coverageItem(sourceType, sourceId, 2, "GROUNDED");
        const current = bootstrap(sourceType, sourceId, [unsupported, partial, grounded]);
        const calls = [];
        const streamPayloads = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/auto-reply-preview")) return Promise.resolve(jsonResponse({ previewKind: "QA_AUTO_REPLIED", wouldBeBlockedBy: [] }));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamPayloads.push(payload);
                const version = itemVersion(payload.requestKey, current, payload.handling);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(calls.filter((call) => call.url.includes("/state")).length));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [unsupported, partial, grounded]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();

        const byKey = Object.fromEntries(streamPayloads.map((payload) => [payload.requestKey, payload]));
        assert.strictEqual(streamPayloads.length, 3);
        assert.strictEqual(byKey[unsupported.requestKey].handling, "ANSWER_FROM_OPERATOR_INPUT");
        assert.ok(byKey[unsupported.requestKey].operatorInstruction.length > 0);
        assert.strictEqual(byKey[partial.requestKey].handling, "ANSWER_SUPPORTED_PART");
        assert.strictEqual(byKey[grounded.requestKey].handling, "ANSWER_WITH_EVIDENCE");
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 1);
    });

    it("never calls assemble when any fill step fails and persists nothing partial", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 603;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [unsupported]);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("error", { message: "LLM 不可用" }));
            }
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();

        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.strictEqual(calls.filter((call) => call.url.includes("/state")).length, 0, "no half-baked durable state on failure");
        assert.match(host.innerHTML, /LLM 不可用|生成失败/);
        assert.match(host.innerHTML, /尚未汇总/);
    });

    it("keeps ordinary grounded assembly persistence unchanged", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 632;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED");
        const current = bootstrap(sourceType, sourceId, [g0, g1]);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = itemVersion(payload.requestKey, current, payload.handling || "ANSWER_WITH_EVIDENCE");
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(calls.filter((call) => call.url.includes("/state")).length));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [g0, g1]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "assemble");
        await settle();

        const stateCalls = calls.filter((call) => call.url.includes("/state"));
        const assembleCalls = calls.filter((call) => call.url.includes("/assemble"));
        assert.strictEqual(assembleCalls.length, 1);
        assert.ok(stateCalls.length >= 2, "ordinary grounded assembly must keep per-item durable saves");
        assert.ok(calls.indexOf(stateCalls[stateCalls.length - 1]) < calls.indexOf(assembleCalls[0]), "per-item durable saves precede the assembly request");
    });

    it("persists nothing when a later one-click item fails after an earlier success", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 630;
        const first = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const second = coverageItem(sourceType, sourceId, 1, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [first, second]);
        const calls = [];
        let streamCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                streamCount += 1;
                const payload = JSON.parse(options.body);
                if (streamCount === 2) {
                    return Promise.resolve(sseResponse("error", { message: "LLM 不可用" }));
                }
                const version = itemVersion(payload.requestKey, current, payload.handling);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, []));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();

        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.strictEqual(calls.filter((call) => call.url.includes("/state")).length, 0, "a failed run must not write any durable state, even after an earlier item succeeded");
        assert.match(host.innerHTML, /LLM 不可用|生成失败/);
    });

    it("persists exactly one complete snapshot after the single assemble of a successful run", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 631;
        const first = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const second = coverageItem(sourceType, sourceId, 1, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [first, second]);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = itemVersion(payload.requestKey, current, payload.handling);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [first, second]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();

        const stateCalls = calls.filter((call) => call.url.includes("/state"));
        const assembleCalls = calls.filter((call) => call.url.includes("/assemble"));
        assert.strictEqual(assembleCalls.length, 1);
        assert.strictEqual(stateCalls.length, 1, "a successful run writes exactly one durable snapshot");
        assert.ok(calls.indexOf(stateCalls[0]) > calls.indexOf(assembleCalls[0]), "the single state write must follow the assemble");
        const lockedKeys = JSON.parse(stateCalls[0].options.body).lockedItems.map((item) => item.requestKey);
        assert.ok(lockedKeys.includes(first.requestKey) && lockedKeys.includes(second.requestKey), "the complete snapshot must include every generated item");
    });

    it("drops the machine-fill badge as soon as the operator edits the handling or the instruction", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 604;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [unsupported]);
        const { window, document } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/auto-reply-preview")) return Promise.resolve(jsonResponse({ previewKind: "QA_AUTO_REPLIED", wouldBeBlockedBy: [] }));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = itemVersion(payload.requestKey, current, payload.handling);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [unsupported]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onChange: () => {},
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /机器代填/);

        // handling change -> full re-render without the badge
        host.dispatchEvent("change", {
            dataset: { role: "handling", requestKey: unsupported.requestKey },
            value: "OMIT"
        });
        await settle();
        assert.doesNotMatch(host.innerHTML, /机器代填/);

        // instruction edit -> header re-rendered without the badge
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /机器代填/);
        const header = new FakeElement(document);
        header.dataset = { role: "item-header" };
        const answer = new FakeElement(document);
        answer.dataset = { role: "answer" };
        const actions = new FakeElement(document);
        actions.dataset = { role: "item-actions" };
        const summary = new FakeElement(document);
        summary.dataset = { role: "summary" };
        const item = new FakeElement(document);
        item.dataset = { role: "item", requestKey: unsupported.requestKey };
        item.querySelectorAll = () => [header, answer, actions];
        host.querySelectorAll = () => [item, summary];
        host.dispatchEvent("input", {
            dataset: { role: "instruction", requestKey: unsupported.requestKey },
            value: "人工改写说明"
        });
        await settle();
        assert.doesNotMatch(header.innerHTML, /机器代填/);
    });

    it("renders the manual-handoff decision and each failed hard gate from the existing preview evidence", async () => {
        const { calls, host } = liveAutoRunFixture(610, () => jsonResponse({
            previewKind: "QA_NO_MATCH",
            intentCode: "qa",
            autoAction: "QA",
            confidence: 50,
            matchedKeywords: [],
            replySubject: null,
            replyBody: null,
            reason: "QA_GROUNDING_GAP",
            matchedRuleIds: [],
            wouldBeBlockedBy: ["RECIPIENT_UNSUBSCRIBED", "AUTO_REPLY_DISABLED"],
            attachmentIntentIgnored: false
        }));
        await settle();
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /汇总已完成/);
        assert.match(host.innerHTML, /判定：转人工/);
        assert.match(host.innerHTML, /RECIPIENT_UNSUBSCRIBED/);
        assert.match(host.innerHTML, /AUTO_REPLY_DISABLED/);
        assert.doesNotMatch(host.innerHTML, /判定：可自动发/);
        assert.strictEqual(calls.filter((call) => /send|manual-rich-reply|qa-reply/.test(call.url)).length, 0, "verdict evidence must never touch the send path");
    });

    it("renders the eligible decision from authoritative preview evidence without equating assembly with clearance", async () => {
        const { calls, host } = liveAutoRunFixture(611, () => jsonResponse({
            previewKind: "QA_AUTO_REPLIED",
            wouldBeBlockedBy: []
        }));
        await settle();
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /汇总已完成/);
        assert.match(host.innerHTML, /判定：可自动发/);
        assert.match(host.innerHTML, /无未通过硬性闸门/);
        assert.strictEqual(calls.filter((call) => /send|manual-rich-reply|qa-reply/.test(call.url)).length, 0);
    });

    it("keeps the badge browser-local while locked-item payloads carry no machine-origin metadata", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 621;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [unsupported]);
        const statePayloads = [];
        const assemblePayloads = [];
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = itemVersion(payload.requestKey, current, payload.handling);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                statePayloads.push(JSON.parse(options.body));
                return Promise.resolve(stateResponse(1));
            }
            if (url.includes("/assemble")) {
                assemblePayloads.push(JSON.parse(options.body));
                return Promise.resolve(assembleResponse(current, []));
            }
            if (url.includes("/auto-reply-preview")) return Promise.resolve(jsonResponse({ previewKind: "QA_AUTO_REPLIED", wouldBeBlockedBy: [] }));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /机器代填/, "the badge stays visible in the active session");

        const lockedItems = [
            ...statePayloads.flatMap((payload) => payload.lockedItems || []),
            ...assemblePayloads.flatMap((payload) => payload.lockedItems || [])
        ];
        assert.ok(lockedItems.length > 0, "the run must lock and send items");
        lockedItems.forEach((item) => {
            assert.ok(!("autoFilled" in item), "locked-item payloads must not carry machine-origin metadata");
        });

        // a restored saved item is always unmarked
        const persisted = statePayloads.flatMap((payload) => payload.lockedItems || [])
            .find((item) => item.requestKey === unsupported.requestKey);
        assert.ok(persisted, "machine-filled item must be durably saved");
        const restored = mountRestored(sourceId, { status: "RESTORED", stateVersion: 1, lockedItems: [persisted] });
        await settle();
        assert.doesNotMatch(restored.host.innerHTML, /机器代填/, "restored saved items are unmarked");

        // an operator edit clears the badge in the live session
        restored.host.dispatchEvent("change", {
            dataset: { role: "handling", requestKey: unsupported.requestKey },
            value: "OMIT"
        });
        await settle();
        assert.doesNotMatch(restored.host.innerHTML, /机器代填/);
    });

    it("keeps the explicit pending verdict state when the preview is unavailable", async () => {
        const { calls, host } = liveAutoRunFixture(612, () => ({ ok: false, status: 404, json: async () => ({}) }));
        await settle();
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /汇总已完成/);
        assert.match(host.innerHTML, /判定：尚未预判/);
        assert.match(host.innerHTML, /硬性闸门：尚未预判/);
        assert.strictEqual(calls.filter((call) => /send|manual-rich-reply|qa-reply/.test(call.url)).length, 0);
    });

    it("auto-reset issues DELETE for the saved state and re-bootstraps to defaults", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 605;
        const unsupported = coverageItem(sourceType, sourceId, 0, "UNSUPPORTED");
        const current = bootstrap(sourceType, sourceId, [unsupported]);
        const calls = [];
        let stateCount = 0;
        let bootstrapCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) {
                bootstrapCount += 1;
                return Promise.resolve(jsonResponse(current));
            }
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = itemVersion(payload.requestKey, current, payload.handling);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                if ((options.method || "POST") === "DELETE") {
                    return Promise.resolve(jsonResponse({ status: "DELETED", stateVersion: 0 }));
                }
                stateCount += 1;
                return Promise.resolve(stateResponse(stateCount));
            }
            if (url.includes("/assemble")) return Promise.resolve(assembleResponse(current, [unsupported]));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "auto-run");
        await settle();
        assert.match(host.innerHTML, /机器代填/);
        assert.match(host.innerHTML, /汇总已完成/);

        click(host, "auto-reset");
        await settle();

        const deleteCalls = calls.filter((call) => call.url.includes("/state") && (call.options.method || "POST") === "DELETE");
        assert.strictEqual(deleteCalls.length, 1);
        assert.strictEqual(JSON.parse(deleteCalls[0].options.body).expectedStateVersion, 1);
        assert.ok(bootstrapCount >= 2, "reset must re-bootstrap");
        assert.doesNotMatch(host.innerHTML, /机器代填/);
        assert.match(host.innerHTML, /尚未汇总/);
        assert.doesNotMatch(host.innerHTML, /assembled draft/);
    });

    it("never renders the one-click bar on the read-only AUTO_PREVIEW host", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 606;
        const current = bootstrap(sourceType, sourceId, [coverageItem(sourceType, sourceId, 0, "UNSUPPORTED")]);
        const { window } = createSandbox((url) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "AUTO_PREVIEW",
            source: current.source,
            contextPath: ""
        });
        await settle();
        assert.doesNotMatch(host.innerHTML, /data-action="auto-run"/);
        assert.doesNotMatch(host.innerHTML, /data-action="auto-reset"/);
        assert.match(host.innerHTML, /只读预览/);
    });

    // I-1 (T-5.1): 单条 stateSavePending 只出该条局部遮罩——busyOverlayState()
    // 返回 null、itemBusyState(该条) 非 null、itemBusyState(其他条) 为 null；
    // 三个真·全局操作（generation/frame/complete）仍出全局遮罩。
    it("masks only the pending item when a single-item save is pending", () => {
        const workbenchSource = fs.readFileSync(componentPath, "utf-8");
        const busyStart = workbenchSource.indexOf("function busyOverlayState()");
        const busyEnd = workbenchSource.indexOf("function itemBusyState(request)");
        const itemEnd = workbenchSource.indexOf("function renderItemBusyOverlay(request)");
        assert.ok(busyStart >= 0 && busyEnd > busyStart && itemEnd > busyEnd, "mask functions must be sliceable");
        const evalMasks = new Function("state", "request", `
            ${workbenchSource.slice(busyStart, busyEnd)}
            ${workbenchSource.slice(busyEnd, itemEnd)}
            return { global: busyOverlayState(), item: itemBusyState(request) };
        `);
        const idleState = {
            stateSavePending: false,
            generation: { pending: false },
            frameSavePending: false,
            completePending: false
        };
        const pendingRequest = { pending: false, factChangePending: false, stateSavePending: true };
        const idleRequest = { pending: false, factChangePending: false, stateSavePending: false };
        const masked = evalMasks(idleState, pendingRequest);
        assert.strictEqual(masked.global, null, "a single-item save must not raise the global mask");
        assert.deepStrictEqual(masked.item, { text: "正在保存本条摘要…", hint: "其他摘要仍可继续操作。" });
        assert.strictEqual(evalMasks(idleState, idleRequest).item, null, "an untouched item must stay unmasked");
        // What must NOT change: 全量重跑/框架保存/整合仍出全局遮罩。
        const generationState = { stateSavePending: false, generation: { pending: true }, frameSavePending: false, completePending: false };
        assert.strictEqual(evalMasks(generationState, pendingRequest).item, null, "a true global operation keeps owning the mask");
        assert.ok(evalMasks(generationState, pendingRequest).global !== null, "generation.pending must keep the global mask");
        const frameState = { stateSavePending: false, generation: { pending: false }, frameSavePending: true, completePending: false };
        assert.strictEqual(evalMasks(frameState, pendingRequest).global.text, "正在保存回复框架…");
        const completeState = { stateSavePending: false, generation: { pending: false }, frameSavePending: false, completePending: true };
        assert.strictEqual(evalMasks(completeState, pendingRequest).global.text, "正在整合整封回复…");
    });

    // I-1 (T-5.1, A-2): 单条采用保存期间，只有该条卡片出现局部遮罩，
    // 整个工作台没有全局遮罩，其余摘要保持可操作。
    it("keeps the global mask hidden while a single-item durable save is pending", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 705;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED");
        const current = bootstrap(sourceType, sourceId, [g0, g1]);
        const saveGate = deferred();
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version: itemVersion(payload.requestKey, current, payload.handling)
                }));
            }
            if (url.includes("/state")) return saveGate.promise.then(() => Promise.resolve(stateResponse(1)));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "adjust-item", g0.requestKey);
        await settle();
        click(host, "resolve-item", g0.requestKey);
        await settle();
        // 保存悬而未决：只有 g0 卡片局部遮罩，无全局遮罩，g1 的按钮可点。
        assert.match(host.innerHTML, /正在保存本条摘要…/);
        assert.doesNotMatch(host.innerHTML, /正在保存工作台状态…/);
        assert.doesNotMatch(host.innerHTML, /class="trust-reply-busy-overlay"/);
        const g0Idx = host.innerHTML.indexOf(`data-role="item" data-request-key="${g0.requestKey}"`);
        const g1Idx = host.innerHTML.indexOf(`data-role="item" data-request-key="${g1.requestKey}"`);
        const busyIdx = host.innerHTML.indexOf("trust-reply-item-busy-overlay");
        assert.ok(g0Idx >= 0 && g1Idx > g0Idx && busyIdx > g0Idx && busyIdx < g1Idx,
            "the local mask must live inside the pending item's card only");
        assert.match(host.innerHTML, new RegExp(`data-action="resolve-item" data-request-key="${g0.requestKey}"[^>]*disabled`));
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-action="adjust-item" data-request-key="${g1.requestKey}"[^>]*disabled`));
        saveGate.resolve();
        await settle();
        assert.match(host.innerHTML, new RegExp(`data-request-key="${g0.requestKey}"[\\s\\S]*?data-locked="true"`));
    });

    // I-5 (T-5.4): data-action="start-analysis" 委托点击后 bootstrap 被调用
    // 恰好一次（DOM stub 盲区之外的行为断言），成功后按钮态切换。
    it("runs bootstrap exactly once when start-analysis is clicked", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 706;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrap(sourceType, sourceId, [grounded]);
        let bootstrapCalls = 0;
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) {
                bootstrapCalls += 1;
                return Promise.resolve(jsonResponse(current));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {},
            autoBootstrap: false
        });
        await settle();
        assert.strictEqual(bootstrapCalls, 0);
        assert.match(host.innerHTML, /尚未分析这封来信/);
        assert.doesNotMatch(host.innerHTML, new RegExp('data-action="start-analysis"[^>]*disabled'));
        assert.match(host.innerHTML, /data-action="auto-run"[^>]*disabled/);

        click(host, "start-analysis");
        await settle();
        await settle();
        assert.strictEqual(bootstrapCalls, 1, "clicking start-analysis must fire exactly one bootstrap");
        assert.doesNotMatch(host.innerHTML, /尚未分析这封来信/);
        assert.match(host.innerHTML, /class="button secondary" data-action="start-analysis">重新分析</);
        assert.doesNotMatch(host.innerHTML, new RegExp('data-action="auto-run"[^>]*disabled'));
        assert.doesNotMatch(host.innerHTML, new RegExp('data-action="auto-reset"[^>]*disabled'));
        assert.match(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"`));
    });
});
