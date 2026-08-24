const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const componentPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const source = fs.readFileSync(componentPath, "utf-8");
const indexPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const indexSource = fs.readFileSync(indexPath, "utf-8");
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
            return [...this.innerHTML.matchAll(/data-role="([^"]+)"/g)].map((match) => ({ dataset: { role: match[1] } }));
        }
        return [];
    }
    querySelector() { return null; }
}

class FakeDocument {
    constructor() { this.activeElement = null; }
    createElement() { return new FakeElement(this); }
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
    return { promise, resolve, reject };
}

function jsonResponse(body) {
    return { ok: true, status: 200, json: async () => body };
}

function sseResponse(event, data) {
    const frame = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    let consumed = false;
    let cancelled = false;
    const reader = {
        async read() {
            if (consumed) return { done: true, value: undefined };
            consumed = true;
            return { done: false, value: new TextEncoder().encode(frame) };
        },
        async cancel() { cancelled = true; }
    };
    return {
        ok: true,
        status: 200,
        body: { getReader: () => reader },
        get readerCancelled() { return cancelled; }
    };
}

function settle() {
    return new Promise((resolve) => setImmediate(() => setImmediate(resolve)));
}

function click(host, action, requestKey, versionId, factId, page) {
    host.dispatchEvent("click", {
        dataset: { action, requestKey, versionId, factId, page },
        closest: () => ({ dataset: { action, requestKey, versionId, factId, page } })
    });
}

function itemVersion(requestKey, sourceVersion, evidenceSetVersion, versionId = "v1") {
    return {
        versionId,
        requestKey,
        handling: "ANSWER_WITH_EVIDENCE",
        answerText: "answer",
        claims: [],
        model: "DEEPSEEK_V4_FLASH",
        generationKind: "AI_GENERATED",
        evidenceSetVersion,
        sourceVersion
    };
}

function lockedItem(requestKey, sourceVersion, evidenceSetVersion, versionId = "v1", overrides = {}) {
    return {
        requestKey,
        versionId,
        handling: "ANSWER_WITH_EVIDENCE",
        answerText: "answer",
        claims: [],
        model: "DEEPSEEK_V4_FLASH",
        generationKind: "AI_GENERATED",
        evidenceSetVersion,
        sourceVersion,
        operatorInstructionHash: "",
        operatorInstruction: "",
        ...overrides
    };
}

function serializeLocked(version, current) {
    return {
        requestKey: version.requestKey,
        versionId: version.versionId,
        handling: version.handling,
        answerText: version.answerText || "",
        claims: version.claims || [],
        model: version.model || "",
        generationKind: version.generationKind,
        evidenceSetVersion: version.evidenceSetVersion,
        sourceVersion: version.sourceVersion,
        operatorInstructionHash: version.operatorInstructionHash || "",
        operatorInstruction: version.operatorInstruction || ""
    };
}

function stateResponse(stateVersion, lockedItems, status) {
    return jsonResponse({
        status: status || "SAVED",
        stateVersion,
        selectedModel: "DEEPSEEK_V4_FLASH",
        requestedFactIds: [1],
        lockedItems: lockedItems || []
    });
}

function conflictResponse(code) {
    return { ok: false, status: 409, json: async () => ({ code: code || "TRUST_REPLY_STATE_CONFLICT" }) };
}

function pendingSseFetch() {
    let resolveFetch;
    let rejectFetch;
    const promise = new Promise((resolve, reject) => { resolveFetch = resolve; rejectFetch = reject; });
    promise.bind = (options) => {
        if (options && options.signal) {
            options.signal.addEventListener("abort", () => {
                rejectFetch(Object.assign(new Error("AbortError"), { name: "AbortError", code: 20 }));
            });
        }
    };
    promise.resolveFetch = (value) => resolveFetch(value);
    return promise;
}

function createSandbox(fetchImpl, { crypto = { randomUUID: () => "00000000-0000-4000-8000-000000000001" } } = {}) {
    const document = new FakeDocument();
    const window = {
        document,
        fetch: fetchImpl,
        confirm: () => true,
        crypto,
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

function bootstrapWithCoverage(sourceType, sourceId, coverageItems) {
    const payload = bootstrap(sourceType, sourceId);
    payload.requestCoverage = coverageItems;
    return payload;
}

function coverageItem(sourceType, sourceId, index, status, requestKeySuffix = "") {
    return {
        index,
        requestKey: `${sourceType}-${sourceId}-request${requestKeySuffix}`,
        requestText: `Question ${index + 1}`,
        status,
        factRuleIds: status === "UNSUPPORTED" ? [] : [1],
        // 03a (I-1): per-request evidence version; tests use the same value as
        // the aggregate so generated versions satisfy hasVersionIdentity.
        evidenceSetVersion: `${sourceType}-${sourceId}-e1`,
        allowedHandlings: status === "UNSUPPORTED"
            ? ["ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"]
            : status === "PARTIAL"
                ? ["ANSWER_SUPPORTED_PART", "OMIT"]
                : ["ANSWER_WITH_EVIDENCE", "OMIT"],
        recommendedHandling: status === "UNSUPPORTED"
            ? "ACKNOWLEDGE_PENDING"
            : status === "PARTIAL"
                ? "ANSWER_SUPPORTED_PART"
                : "ANSWER_WITH_EVIDENCE"
    };
}

const bootstrap = (sourceType, sourceId) => ({
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
    requestCoverage: [{
        index: 0,
        requestKey: `${sourceType}-${sourceId}-request`,
        requestText: "Question",
        status: "GROUNDED",
        factRuleIds: [1],
        evidenceSetVersion: `${sourceType}-${sourceId}-e1`,
        allowedHandlings: ["ANSWER_WITH_EVIDENCE", "OMIT"],
        recommendedHandling: "ANSWER_WITH_EVIDENCE"
    }],
    draftReadiness: "READY",
    contextWarnings: [],
    evidenceSetVersion: `${sourceType}-${sourceId}-e1`
});

const DEFAULT_FRAME_SNAPSHOT = {
    selection: { salutationSnippetId: 11, greetingSnippetId: 21, ackSnippetId: null, closingSnippetId: 41 },
    version: "frame-v1"
};

const DEFAULT_FRAME_OPTIONS = [
    { id: 11, snippetType: "SALUTATION", content: "尊敬的专家", displayOrder: 1, isDefault: true },
    { id: 21, snippetType: "GREETING", content: "您好", displayOrder: 1, isDefault: true },
    { id: 22, snippetType: "GREETING", content: "您好呀", displayOrder: 2, isDefault: false },
    { id: 31, snippetType: "ACK", content: "感谢您的来信", displayOrder: 1, isDefault: false },
    { id: 41, snippetType: "CLOSING", content: "此致敬礼", displayOrder: 1, isDefault: true }
];

function bootstrapWithFrame(sourceType, sourceId, coverageItems) {
    const current = bootstrapWithCoverage(sourceType, sourceId, coverageItems);
    current.frameOptions = DEFAULT_FRAME_OPTIONS.map((option) => ({ ...option }));
    current.frameSnapshot = {
        selection: { ...DEFAULT_FRAME_SNAPSHOT.selection },
        version: DEFAULT_FRAME_SNAPSHOT.version
    };
    current.requestFactSelections = coverageItems.map((item) => ({
        requestKey: item.requestKey,
        factRuleIds: [...(item.factRuleIds || [])]
    }));
    return current;
}

function frameStateResponse(stateVersion, frameSnapshot, lockedItems) {
    return jsonResponse({
        status: "SAVED",
        stateVersion,
        selectedModel: "DEEPSEEK_V4_FLASH",
        requestedFactIds: [1],
        lockedItems: lockedItems || [],
        frameSnapshot
    });
}

describe("shared trust reply workbench mount contract", () => {
    it("reorders a dragged fact before the target when the drop is on the target's left half", () => {
        const { window } = createSandbox(() => Promise.reject(new Error("not used")));
        assert.deepStrictEqual(
            Array.from(window.TrustReplyWorkbench.resolveFactDrop([1, 2, 3], 3, 1, true)),
            [3, 1, 2]
        );
    });

    it("explains why fact actions are blocked while a shared save is pending", () => {
        const { window } = createSandbox(() => Promise.reject(new Error("not used")));
        assert.strictEqual(
            window.TrustReplyWorkbench.factActionBlockReason({ stateSavePending: true }),
            "正在保存工作台状态，完成后可调整事实"
        );
    });

    it("shows a fact-update busy state until the canonical matrix reload finishes", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 513;
        const current = bootstrap(sourceType, sourceId);
        const reload = deferred();
        let bootstrapCalls = 0;
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) {
                bootstrapCalls += 1;
                return bootstrapCalls === 1 ? Promise.resolve(jsonResponse(current)) : reload.promise;
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
        await settle();

        click(host, "remove-fact", current.requestCoverage[0].requestKey, undefined, "1");
        assert.match(host.innerHTML, /正在更新事实，完成后可继续调整/);
        reload.resolve(jsonResponse({ ...current, requestCoverage: [{ ...current.requestCoverage[0], factRuleIds: [] }], requestFactSelections: [{ requestKey: current.requestCoverage[0].requestKey, factRuleIds: [] }] }));
        await settle();
        await settle();
        assert.doesNotMatch(host.innerHTML, /正在更新事实，完成后可继续调整/);
    });

    it("loads the runtime relative to the deployed context and guards both host mounts", () => {
        assert.doesNotMatch(indexSource, /src="\/trust-reply-workbench\.js/);
        const runtimeMatch = indexSource.match(/src="trust-reply-workbench\.js\?v=([^"]+)"/);
        const appMatch = indexSource.match(/src="app\.js\?v=([^"]+)"/);
        const stylesMatch = indexSource.match(/href="styles\.css\?v=([^"]+)"/);
        assert.ok(runtimeMatch, "shared runtime must use a context-relative cache-busted URL");
        assert.ok(appMatch, "app.js must have a cache key");
        assert.ok(stylesMatch, "styles.css must have a cache key");
        assert.strictEqual(runtimeMatch[1], appMatch[1]);
        assert.strictEqual(runtimeMatch[1], stylesMatch[1]);
        assert.ok(indexSource.indexOf(runtimeMatch[0]) < indexSource.indexOf(appMatch[0]));
        assert.match(appSource, /function requireTrustReplyWorkbenchRuntime\(host\)/);
        assert.strictEqual((appSource.match(/requireTrustReplyWorkbenchRuntime\(host\)/g) || []).length, 3);
        assert.match(appSource, /可信回复工作台资源加载失败，请刷新页面后重试/);
    });

    it("exports one idempotent namespace and uses a fixed internal role tree", async () => {
        const pendingTraining = deferred();
        const pendingLive = deferred();
        const { sandbox, window } = createSandbox((url, options) => {
            const request = JSON.parse(options.body);
            return request.source.sourceType === "TRAINING_MAIL" ? pendingTraining.promise : pendingLive.promise;
        });
        assert.ok(window.TrustReplyWorkbench);
        assert.strictEqual(Object.keys(window).filter((key) => key === "TrustReplyWorkbench").length, 1);
        assert.doesNotThrow(() => vm.runInContext(source, sandbox));
        assert.doesNotMatch(source, /document\.write/);
        assert.doesNotMatch(source, /(^|\n)\s*(?:const|let|var)\s+\$\b/);

        const trainingHost = new FakeElement(window.document);
        const liveHost = new FakeElement(window.document);
        const training = window.TrustReplyWorkbench.mount(trainingHost, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        const live = window.TrustReplyWorkbench.mount(liveHost, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 202 },
            contextPath: "",
            onComplete: async () => {}
        });
        pendingTraining.resolve({ ok: true, status: 200, json: async () => bootstrap("TRAINING_MAIL", 101) });
        pendingLive.resolve({ ok: true, status: 200, json: async () => bootstrap("LIVE_INBOUND", 202) });
        await new Promise((resolve) => setImmediate(resolve));
        assert.ok(trainingHost.innerHTML.includes("模拟 · 不外发"));
        assert.ok(liveHost.innerHTML.includes("正式回复") || liveHost.innerHTML.includes("加载工作台"));
        const trainingRoles = [...trainingHost.innerHTML.matchAll(/data-role="([^"]+)"/g)].map((match) => match[1]);
        const liveRoles = [...liveHost.innerHTML.matchAll(/data-role="([^"]+)"/g)].map((match) => match[1]);
        assert.deepStrictEqual(trainingRoles, liveRoles);
        assert.doesNotMatch(trainingHost.innerHTML, /mode-switch|mode-selector|radio/i);
        assert.doesNotMatch(liveHost.innerHTML, /mode-switch|mode-selector|radio/i);
        assert.doesNotThrow(() => training.unmount());
        assert.doesNotThrow(() => training.unmount());
        live.unmount();
    });

    it("sends exact source identity and drops a late bootstrap after unmount", async () => {
        const request = deferred();
        let payload;
        const { window } = createSandbox((url, options) => {
            payload = JSON.parse(options.body);
            return request.promise;
        });
        const host = new FakeElement(window.document);
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 9876 },
            contextPath: "/console",
            onComplete: async () => {}
        });
        assert.strictEqual(payload.source.sourceType, "TRAINING_MAIL");
        assert.strictEqual(payload.source.sourceId, 9876);
        assert.doesNotMatch(JSON.stringify(payload), /contactId|latest/i);
        instance.unmount();
        request.resolve({ ok: true, status: 200, json: async () => bootstrap("TRAINING_MAIL", 9876) });
        await new Promise((resolve) => setImmediate(resolve));
        assert.strictEqual(host.innerHTML, "");
    });

    it("rejects invalid mode and source combinations", () => {
        const { window } = createSandbox(() => Promise.resolve({ ok: true, status: 200, json: async () => ({}) }));
        const host = new FakeElement(window.document);
        assert.throws(() => window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "LIVE_INBOUND", sourceId: 1 },
            contextPath: "",
            onComplete: async () => {}
        }), /来源|模式|source|mode/i);
        assert.throws(() => window.TrustReplyWorkbench.mount(new FakeElement(window.document), {
            mode: "AUTO_PREVIEW",
            source: { sourceType: "TRAINING_MAIL", sourceId: 1 },
            contextPath: ""
        }), /来源|模式|source|mode/i);
        assert.throws(() => window.TrustReplyWorkbench.mount(new FakeElement(window.document), {
            mode: "UNKNOWN_MODE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 1 },
            contextPath: ""
        }), /模式无效|模式/i);
    });

    it("keeps the generation id canonical when randomUUID is unavailable", async () => {
        const current = bootstrap("TRAINING_MAIL", 301);
        const calls = [];
        const responses = [
            jsonResponse(current),
            { ok: false, status: 400, json: async () => ({ code: "EXPECTED_TEST_STOP" }) }
        ];
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            calls.push({ url, options });
            return Promise.resolve(responses.shift());
        }, { crypto: null });
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

        const payload = JSON.parse(calls.find((call) => call.url.includes("/generations/stream")).options.body);
        assert.match(payload.generationId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    });

    it("allows the SSE endpoint to return a structured JSON error", async () => {
        const current = bootstrap("TRAINING_MAIL", 302);
        const calls = [];
        const responses = [
            jsonResponse(current),
            { ok: false, status: 400, json: async () => ({ code: "TRUST_REPLY_GENERATION_ID_INVALID" }) }
        ];
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            calls.push({ url, options });
            return Promise.resolve(responses.shift());
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

        assert.strictEqual(calls.find((call) => call.url.includes("/generations/stream")).options.headers.Accept, "text/event-stream, application/json");
        assert.match(host.innerHTML, /TRUST_REPLY_GENERATION_ID_INVALID/);
    });

    it("writes global model and timeout controls into the generation payload", async () => {
        const current = bootstrap("TRAINING_MAIL", 304);
        current.availableModels = ["DEEPSEEK_V4_FLASH", "DEEPSEEK_V4_PRO"];
        current.requestCoverage[0].status = "UNSUPPORTED";
        current.requestCoverage[0].allowedHandlings = ["ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"];
        current.requestCoverage[0].recommendedHandling = "ACKNOWLEDGE_PENDING";
        const calls = [];
        const responses = [
            jsonResponse(current),
            { ok: false, status: 400, json: async () => ({ code: "EXPECTED_TEST_STOP" }) }
        ];
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            calls.push({ url, options });
            return Promise.resolve(responses.shift());
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        host.dispatchEvent("change", { dataset: { role: "model" }, value: "DEEPSEEK_V4_PRO" });
        host.dispatchEvent("change", { dataset: { role: "attempt-timeout" }, value: "60" });
        host.dispatchEvent("change", { dataset: { role: "total-timeout" }, value: "600" });
        click(host, "adjust-item", current.requestCoverage[0].requestKey);
        await settle();

        const payload = JSON.parse(calls[1].options.body);
        assert.strictEqual(payload.model, "DEEPSEEK_V4_PRO");
        assert.strictEqual(payload.llmAttemptTimeoutSeconds, 60);
        assert.strictEqual(payload.llmTotalTimeoutSeconds, 600);
    });

    it("does not auto-generate on mount and shows grounded items as pending generation", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 306;
        const current = bootstrapWithCoverage(sourceType, sourceId, [
            coverageItem(sourceType, sourceId, 0, "GROUNDED"),
            coverageItem(sourceType, sourceId, 1, "PARTIAL", "-partial"),
            coverageItem(sourceType, sourceId, 2, "UNSUPPORTED", "-unsupported")
        ]);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "问题译文" }));
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
        await settle();

        assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream")).length, 0);
        assert.match(host.innerHTML, /待生成/);
        assert.match(host.innerHTML, /待处理/);
        assert.match(host.innerHTML, /data-role="item-body" hidden/);
        assert.match(host.innerHTML, /data-action="adjust-item"/);
        assert.doesNotMatch(host.innerHTML, /data-action="generate-all"/);
    });

    it("does not generate a full draft for all unsupported items and translates the question", async () => {
        const current = bootstrap("LIVE_INBOUND", 307);
        current.requestCoverage[0].status = "UNSUPPORTED";
        current.requestCoverage[0].allowedHandlings = ["ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"];
        current.requestCoverage[0].recommendedHandling = "ANSWER_FROM_OPERATOR_INPUT";
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "问题译文" }));
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
        await settle();

        const bodies = calls.map((call) => JSON.parse(call.options.body));
        assert.strictEqual(bodies.filter((payload) => payload.operation === "FULL_DRAFT").length, 0);
        assert.ok(bodies.some((payload) => payload.text === "Question"));
        assert.match(host.innerHTML, /回答说明（AI 将仅据此生成）/);
        assert.match(host.innerHTML, /问题译文/);
        assert.match(host.innerHTML, /data-role="item-body"/);
        assert.doesNotMatch(host.innerHTML, /data-role="item-body" hidden/);
    });

    it("keeps an unsupported answer action clickable and explains when its description is missing", async () => {
        const current = bootstrap("LIVE_INBOUND", 309);
        current.requestCoverage[0].status = "UNSUPPORTED";
        current.requestCoverage[0].allowedHandlings = ["ANSWER_FROM_OPERATOR_INPUT", "OMIT"];
        current.requestCoverage[0].recommendedHandling = "ANSWER_FROM_OPERATOR_INPUT";
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "问题译文" }));
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

        const actionButton = host.innerHTML.match(/<button[^>]*data-action="adjust-item"[^>]*>[^<]*<\/button>/)?.[0];
        assert.ok(actionButton, "unsupported item must render an AI generation action");
        assert.doesNotMatch(actionButton, /\sdisabled/);
        click(host, "adjust-item", current.requestCoverage[0].requestKey);
        await settle();

        assert.match(host.innerHTML, /请先填写回答说明/);
        assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream")).length, 0);
    });

    it("keeps an unsupported answer action clickable and explains when full generation is running", async () => {
        const current = bootstrap("LIVE_INBOUND", 310);
        const requestKey = current.requestCoverage[0].requestKey;
        const fullGeneration = deferred();
        const { window } = createSandbox((url, options) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "问题译文" }));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/generations/stream")) return fullGeneration.promise;
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
        click(host, "assemble");
        await settle();

        const actionButton = host.innerHTML.match(/<button[^>]*data-action="adjust-item"[^>]*>[^<]*<\/button>/)?.[0];
        assert.ok(actionButton, "grounded item must render an AI generation action");
        click(host, "adjust-item", requestKey);
        await settle();

        assert.match(host.innerHTML, /正在生成其他回复，请稍后/);
        fullGeneration.resolve(sseResponse("result", {
            source: current.source,
            sourceVersion: current.sourceVersion,
            evidenceSetVersion: current.evidenceSetVersion,
            itemVersions: [itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion)]
        }));
        await settle();
    });

    it("keeps operator instruction focus while invalidating only its resolved assembly", async () => {
        const current = bootstrap("LIVE_INBOUND", 308);
        const first = current.requestCoverage[0];
        first.status = "UNSUPPORTED";
        first.allowedHandlings = ["ANSWER_FROM_OPERATOR_INPUT", "OMIT"];
        first.recommendedHandling = "ANSWER_FROM_OPERATOR_INPUT";
        const second = {
            ...first,
            index: 1,
            requestKey: `${current.source.sourceType}-${current.source.sourceId}-request-2`,
            requestText: "Question two"
        };
        current.requestCoverage.push(second);
        const firstVersion = {
            ...itemVersion(first.requestKey, current.sourceVersion, current.evidenceSetVersion, "first-v1"),
            handling: "ANSWER_FROM_OPERATOR_INPUT",
            operatorInstruction: "old first"
        };
        const secondVersion = {
            ...itemVersion(second.requestKey, current.sourceVersion, current.evidenceSetVersion, "second-v1"),
            handling: "ANSWER_FROM_OPERATOR_INPUT",
            operatorInstruction: "old second"
        };
        const nextFirstVersion = {
            ...firstVersion,
            versionId: "first-v2",
            operatorInstruction: "first second"
        };
        const generationPayloads = [];
        const { window, document } = createSandbox((url, options) => {
            const payload = JSON.parse(options.body);
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/generations/stream")) {
                generationPayloads.push(payload);
                const version = payload.requestKey === first.requestKey && payload.operatorInstruction === "first second"
                    ? nextFirstVersion : payload.requestKey === first.requestKey ? firstVersion : secondVersion;
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    itemVersions: [version]
                }));
            }
            if (url.includes("/assemble")) return Promise.resolve(jsonResponse({
                source: current.source,
                sourceVersion: current.sourceVersion,
                evidenceSetVersion: current.evidenceSetVersion,
                rawDraftText: "server draft",
                renderedDraftText: "server draft",
                draftHash: "hash",
                canonicalFactIds: [1],
                itemVersions: [firstVersion, secondVersion]
            }));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        let changes = 0;
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onChange: () => { changes += 1; },
            onComplete: async () => {}
        });
        await settle();

        for (const [request, instruction] of [[first, "old first"], [second, "old second"]]) {
            host.dispatchEvent("input", { dataset: { role: "instruction", requestKey: request.requestKey }, value: instruction });
            click(host, "adjust-item", request.requestKey);
            await settle();
            click(host, "resolve-item", request.requestKey);
            await settle();
        }
        click(host, "assemble");
        await settle();
        assert.match(host.innerHTML, /server draft/);

        const makeRenderedItem = (request, version, locked) => {
            const item = new FakeElement(document);
            item.dataset = { role: "item", requestKey: request.requestKey, locked: String(locked) };
            const header = new FakeElement(document);
            header.dataset = { role: "item-header" };
            const body = new FakeElement(document);
            body.dataset = { role: "item-body" };
            body.hidden = false;
            const translation = new FakeElement(document);
            translation.dataset = { role: "translation-text" };
            translation.innerHTML = "相邻项译文";
            const actions = new FakeElement(document);
            actions.dataset = { role: "item-actions" };
            const versionSelect = new FakeElement(document);
            versionSelect.dataset = { role: "version", requestKey: request.requestKey };
            versionSelect.value = version.versionId;
            const answer = new FakeElement(document);
            answer.dataset = { role: "answer" };
            answer.innerHTML = `<div>${version.answerText}</div>`;
            item.querySelectorAll = () => [header, body, translation, actions, versionSelect, answer];
            return { item, body, translation, actions, versionSelect, answer };
        };
        const firstDom = makeRenderedItem(first, firstVersion, true);
        const secondDom = makeRenderedItem(second, secondVersion, true);
        const summary = new FakeElement(document);
        summary.dataset = { role: "summary" };
        summary.innerHTML = "server draft";
        host.querySelectorAll = () => [firstDom.item, secondDom.item, summary];

        const textarea = { ownerHost: host, dataset: { role: "instruction", requestKey: first.requestKey }, value: "first" };
        document.activeElement = textarea;
        host.dispatchEvent("input", textarea);
        assert.strictEqual(document.activeElement, textarea);
        textarea.value = "first second";
        host.dispatchEvent("input", textarea);
        await settle();
        assert.strictEqual(document.activeElement, textarea);
        assert.strictEqual(firstDom.versionSelect.value, "");
        assert.doesNotMatch(firstDom.answer.innerHTML, /old first/);
        assert.match(firstDom.answer.innerHTML, /尚未生成版本/);
        assert.match(firstDom.actions.innerHTML, /AI 生成回答/);
        assert.doesNotMatch(summary.innerHTML, /server draft/);
        assert.strictEqual(secondDom.versionSelect.value, secondVersion.versionId);
        assert.match(secondDom.answer.innerHTML, /answer/);
        assert.strictEqual(secondDom.item.dataset.locked, "true");
        assert.strictEqual(secondDom.body.hidden, false);
        assert.match(secondDom.translation.innerHTML, /相邻项译文/);
        assert.ok(changes > 0);

        click(host, "toggle-item", first.requestKey);
        assert.doesNotMatch(host.innerHTML, /server draft/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[\\s\\S]*?data-locked="true"`));
        click(host, "adjust-item", first.requestKey);
        await settle();
        assert.strictEqual(generationPayloads.at(-1).operatorInstruction, "first second");
    });

    it("materializes and locks an OMIT decision after item generation fails", async () => {
        const current = bootstrap("LIVE_INBOUND", 305);
        current.requestCoverage[0].status = "UNSUPPORTED";
        current.requestCoverage[0].allowedHandlings = ["ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"];
        current.requestCoverage[0].recommendedHandling = "ACKNOWLEDGE_PENDING";
        const requestKey = current.requestCoverage[0].requestKey;
        const omitted = {
            ...itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "omit-v1"),
            handling: "OMIT",
            answerText: "",
            generationKind: "OMITTED"
        };
        const calls = [];
        const responses = [
            jsonResponse(current),
            sseResponse("error", { message: "AI generation failed" }),
            sseResponse("result", {
                source: current.source,
                sourceVersion: current.sourceVersion,
                evidenceSetVersion: current.evidenceSetVersion,
                itemVersions: [omitted]
            })
        ];
        const { window } = createSandbox((url, options) => {
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            calls.push({ url, options });
            return Promise.resolve(responses.shift());
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        host.dispatchEvent("change", { dataset: { role: "handling", requestKey }, value: "ACKNOWLEDGE_PENDING" });
        click(host, "adjust-item", requestKey);
        await settle();
        assert.match(host.innerHTML, /AI generation failed/);        host.dispatchEvent("change", { dataset: { role: "handling", requestKey }, value: "OMIT" });
        const lockButton = host.innerHTML.match(/<button[^>]*data-action="resolve-item"[^>]*>[^<]*<\/button>/)?.[0];
        assert.ok(lockButton);
        assert.doesNotMatch(lockButton, /\sdisabled/);
        assert.match(lockButton, /确认省略/);

        click(host, "resolve-item", requestKey);
        await settle();

        const payload = JSON.parse(calls[2].options.body);
        assert.strictEqual(payload.operation, "ADJUST_ITEM");
        assert.strictEqual(payload.handling, "OMIT");
        assert.match(host.innerHTML, /data-locked="true"/);
        assert.match(host.innerHTML, /已处理 1\/1/);
        assert.match(host.innerHTML, /已省略/);
        assert.match(host.innerHTML, /取消省略/);
    });

    it("rejects a foreign item result before it can become a version or lock", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 303;
        const current = bootstrap(sourceType, sourceId);
        current.requestCoverage[0].status = "UNSUPPORTED";
        current.requestCoverage[0].allowedHandlings = ["ACKNOWLEDGE_PENDING", "OMIT"];
        current.requestCoverage[0].recommendedHandling = "ACKNOWLEDGE_PENDING";
        const requestKey = current.requestCoverage[0].requestKey;
        const responses = [
            jsonResponse(current),
            sseResponse("result", {
                source: { sourceType, sourceId },
                sourceVersion: "foreign-source-v9",
                evidenceSetVersion: "foreign-evidence-v9",
                version: itemVersion("foreign-request-key", "foreign-source-v9", "foreign-evidence-v9", "foreign-v1")
            })
        ];
        const { window } = createSandbox((url) => url.includes("/api/translate")
            ? Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }))
            : Promise.resolve(responses.shift()));
        window.confirm = () => false;
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType, sourceId },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "adjust-item", requestKey);
        await settle();
        assert.doesNotMatch(host.innerHTML, /foreign-v1|版本 1/);
        assert.match(host.innerHTML, /来源或事实已变化|身份|生成失败/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
    });

    it("rejects a full result that omits terminal identity", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 350;
        const current = bootstrap(sourceType, sourceId);
        const requestKey = current.requestCoverage[0].requestKey;
        const responses = [
            jsonResponse(current),
            sseResponse("result", { itemVersions: [itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion)] })
        ];
        const { window } = createSandbox(() => Promise.resolve(responses.shift()));
        window.confirm = () => false;
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType, sourceId },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "assemble");
        await settle();
        assert.doesNotMatch(host.innerHTML, /版本 1|value="v1"/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
        assert.match(host.innerHTML, /来源或事实已变化/);
    });

    it("assembles after the grounded sequence and invalidates assembly when a decision changes", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 404;
        const current = bootstrap(sourceType, sourceId);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion);
        const responses = [
            jsonResponse(current),
            sseResponse("result", {
                source: current.source,
                sourceVersion: current.sourceVersion,
                evidenceSetVersion: current.evidenceSetVersion,
                version
            }),
            jsonResponse({
                source: current.source,
                sourceVersion: current.sourceVersion,
                evidenceSetVersion: current.evidenceSetVersion,
                rawDraftText: "server draft",
                renderedDraftText: "server draft",
                draftHash: "hash",
                canonicalFactIds: [1],
                itemVersions: [version]
            })
        ];
        const { window } = createSandbox((url, options) => {
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            return Promise.resolve(responses.shift());
        });
        window.confirm = () => false;
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType, sourceId },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "assemble");
        await settle();
        assert.match(host.innerHTML, /server draft/);
        host.dispatchEvent("change", { dataset: { role: "handling", requestKey }, value: "OMIT" });
        await settle();
        assert.doesNotMatch(host.innerHTML, /server draft/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
    });

    it("keeps the shared card and the S-1..S-6 scoped style contract", () => {
        const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
        const styles = fs.readFileSync(stylesPath, "utf-8");
        assert.match(source, /class="compose-panel trust-reply-item"/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-page-nav\s*\{/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-page-tab\s*\{/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-fact-chip\s*\{/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-fact-picker-option\s*\{/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-fact-picker-option\[data-state="used"\]/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-fact-action-status\s*\{/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-fact-head > \[data-action="toggle-fact-picker"\]:disabled\s*\{/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-preview-state\[data-state="CURRENT"\]/);
        assert.match(styles, /\.trust-reply-workbench \.trust-reply-frame-preview \.trust-reply-summary/);
        assert.doesNotMatch(styles, /\.trust-reply-layout\s*\{/);
        assert.doesNotMatch(styles, /\.trust-reply-fact-option\s*\{/);
        assert.doesNotMatch(styles, /\.trust-reply-toolbar \.compose-rule-list\[data-role="facts"\]/);
    });

    it("requires adopt after single-item generation", async () => {
        const current = bootstrap("TRAINING_MAIL", 360);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
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
        click(host, "adjust-item", requestKey);
        await settle();

        const payload = JSON.parse(calls.find((call) => call.url.includes("/generations/stream")).options.body);
        assert.strictEqual(payload.operation, "ADJUST_ITEM");
        assert.match(host.innerHTML, /采用此版本/);
        assert.doesNotMatch(host.innerHTML, /data-locked="true"/);
        click(host, "resolve-item", requestKey);
        await settle();
        assert.match(host.innerHTML, /data-locked="true"/);
    });

    it("durably saves every locked decision and never falls back to FULL_DRAFT during assemble", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 361;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const partial = coverageItem(sourceType, sourceId, 1, "PARTIAL", "-partial");
        const current = bootstrapWithCoverage(sourceType, sourceId, [grounded, partial]);
        const partialVersion = {
            ...itemVersion(partial.requestKey, current.sourceVersion, current.evidenceSetVersion, "partial-v1"),
            handling: "ANSWER_SUPPORTED_PART"
        };
        const groundedVersion = itemVersion(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "grounded-v1");
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version: payload.requestKey === partial.requestKey ? partialVersion : groundedVersion
                }));
            }
            if (url.includes("/state")) {
                return Promise.resolve(stateResponse(calls.filter((call) => call.url.includes("/state")).length));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "merged draft",
                    renderedDraftText: "merged draft",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [groundedVersion, partialVersion]
                }));
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
        click(host, "adjust-item", partial.requestKey);
        await settle();
        click(host, "resolve-item", partial.requestKey);
        await settle();
        click(host, "assemble");
        await settle();

        const streamCalls = calls.filter((call) => call.url.includes("/generations/stream"));
        const stateCalls = calls.filter((call) => call.url.includes("/state"));
        const assembleCalls = calls.filter((call) => call.url.includes("/assemble"));
        assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream") && JSON.parse(call.options.body).operation === "FULL_DRAFT").length, 0);
        assert.strictEqual(streamCalls.length, 2);
        assert.strictEqual(streamCalls[1].url.includes("/generations/stream"), true);
        assert.strictEqual(JSON.parse(streamCalls[1].options.body).operation, "ADJUST_ITEM");
        assert.strictEqual(JSON.parse(streamCalls[1].options.body).requestKey, grounded.requestKey);
        assert.strictEqual(JSON.parse(streamCalls[1].options.body).handling, "ANSWER_WITH_EVIDENCE");
        assert.strictEqual(stateCalls.length, 2);
        assert.strictEqual(assembleCalls.length, 1);
        const assembleIndex = calls.indexOf(assembleCalls[0]);
        const lastStateIndex = calls.indexOf(stateCalls[1]);
        assert.ok(lastStateIndex < assembleIndex, "durable save must complete before assemble");
        assert.match(host.innerHTML, /merged draft/);
        assert.match(host.innerHTML, /partial-v1/);
    });

    it("adopts active grounded versions during assemble without regenerating them", async () => {
        const current = bootstrap("LIVE_INBOUND", 362);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "active-v1");
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                return Promise.resolve(stateResponse(1, [serializeLocked(version, current)]));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "active adopted",
                    renderedDraftText: "active adopted",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [version]
                }));
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
        await settle();
        click(host, "adjust-item", requestKey);
        await settle();
        click(host, "assemble");
        await settle();

        assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream") && JSON.parse(call.options.body).operation === "FULL_DRAFT").length, 0);
        const stateIndex = calls.findIndex((call) => call.url.includes("/state"));
        const assembleIndex = calls.findIndex((call) => call.url.includes("/assemble"));
        assert.ok(stateIndex >= 0 && stateIndex < assembleIndex, "active grounded adoption must be durably saved before assemble");
        assert.match(host.innerHTML, /active adopted/);
    });

    it("keeps textarea stable while typing instructions", async () => {
        const current = bootstrap("LIVE_INBOUND", 363);
        const request = current.requestCoverage[0];
        const version = itemVersion(request.requestKey, current.sourceVersion, current.evidenceSetVersion, "stable-v1");
        const { window, document } = createSandbox((url, options) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "assembled",
                    renderedDraftText: "assembled",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [version]
                }));
            }
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        let changes = 0;
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: current.source,
            contextPath: "",
            onChange: () => { changes += 1; },
            onComplete: async () => {}
        });
        await settle();
        click(host, "adjust-item", request.requestKey);
        await settle();
        click(host, "resolve-item", request.requestKey);
        await settle();
        click(host, "assemble");
        await settle();

        const header = new FakeElement(document);
        header.dataset = { role: "item-header" };
        const answer = new FakeElement(document);
        answer.dataset = { role: "answer" };
        const actions = new FakeElement(document);
        actions.dataset = { role: "item-actions" };
        const summary = new FakeElement(document);
        summary.dataset = { role: "summary" };
        summary.innerHTML = "assembled";
        const item = new FakeElement(document);
        item.dataset = { role: "item", requestKey: request.requestKey };
        const neighbor = new FakeElement(document);
        neighbor.dataset = { role: "item", requestKey: "neighbor-card" };
        const neighborTranslation = new FakeElement(document);
        neighborTranslation.dataset = { role: "translation-text" };
        neighborTranslation.innerHTML = "相邻项译文";
        item.querySelectorAll = () => [header, answer, actions];
        neighbor.querySelectorAll = () => [neighborTranslation];
        host.querySelectorAll = () => [item, neighbor, summary];

        const textarea = {
            ownerHost: host,
            dataset: { role: "instruction", requestKey: request.requestKey },
            value: "",
            selectionStart: 0,
            selectionEnd: 0
        };
        document.activeElement = textarea;
        const text = "一二三四五六七八九十一二三四五六七八九十";
        let firstInvalidationWrites = 0;
        let writesAfterFirst = 0;
        for (let index = 0; index < text.length; index += 1) {
            textarea.value = text.slice(0, index + 1);
            textarea.selectionStart = textarea.selectionEnd = index + 1;
            const beforeHeader = header.innerHTMLWriteCount;
            const beforeAnswer = answer.innerHTMLWriteCount;
            const beforeActions = actions.innerHTMLWriteCount;
            const beforeSummary = summary.innerHTMLWriteCount;
            host.dispatchEvent("input", textarea);
            const delta = (header.innerHTMLWriteCount - beforeHeader)
                + (answer.innerHTMLWriteCount - beforeAnswer)
                + (actions.innerHTMLWriteCount - beforeActions)
                + (summary.innerHTMLWriteCount - beforeSummary);
            if (index === 0) firstInvalidationWrites = delta;
            else writesAfterFirst += delta;
        }
        assert.strictEqual(textarea.value, text);
        assert.strictEqual(document.activeElement, textarea);
        assert.strictEqual(textarea.selectionStart, text.length);
        assert.strictEqual(textarea.selectionEnd, text.length);
        assert.ok(firstInvalidationWrites > 0);
        assert.strictEqual(writesAfterFirst, 0);
        assert.strictEqual(neighbor.dataset.requestKey, "neighbor-card");
        assert.match(neighborTranslation.innerHTML, /相邻项译文/);
        assert.strictEqual(changes, 1);
    });

    async function mountResolvedPartialWithMissingGrounded(sourceId) {
        const sourceType = "TRAINING_MAIL";
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const partial = coverageItem(sourceType, sourceId, 1, "PARTIAL", "-partial");
        const current = bootstrapWithCoverage(sourceType, sourceId, [grounded, partial]);
        const partialVersion = {
            ...itemVersion(partial.requestKey, current.sourceVersion, current.evidenceSetVersion, "partial-v1"),
            handling: "ANSWER_SUPPORTED_PART"
        };
        const calls = [];
        const hostRef = { host: null };
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                if (payload.operation === "ADJUST_ITEM" && payload.requestKey === partial.requestKey) {
                    return Promise.resolve(sseResponse("result", {
                        source: current.source,
                        sourceVersion: current.sourceVersion,
                        evidenceSetVersion: current.evidenceSetVersion,
                        version: partialVersion
                    }));
                }
                return hostRef.streamResponse ? hostRef.streamResponse(current, payload) : Promise.reject(new Error("missing stream response"));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "assembled",
                    renderedDraftText: "assembled",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [partialVersion]
                }));
            }
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        hostRef.host = host;
        hostRef.window = window;
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        host.dispatchEvent("input", { dataset: { role: "instruction", requestKey: partial.requestKey }, value: "manual note" });
        click(host, "adjust-item", partial.requestKey);
        await settle();
        click(host, "resolve-item", partial.requestKey);
        await settle();
        return { current, grounded, partial, partialVersion, calls, host, hostRef, window };
    }

    it("preserves manual decisions when the assembly sequence is cancelled", async () => {
        const { current, grounded, partial, partialVersion, calls, host, hostRef } = await mountResolvedPartialWithMissingGrounded(370);
        hostRef.streamResponse = () => Promise.resolve(sseResponse("cancelled", { message: "cancelled" }));
        click(host, "assemble");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /manual note/);
        assert.match(host.innerHTML, /partial-v1/);
        assert.match(host.innerHTML, /已取消生成，可重试/);
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[^>]*data-locked="true"`));
    });

    it("preserves manual decisions when an item result omits terminal identity", async () => {
        const { current, grounded, partial, partialVersion, calls, host, hostRef } = await mountResolvedPartialWithMissingGrounded(371);
        const groundedVersion = itemVersion(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "grounded-v1");
        hostRef.streamResponse = () => Promise.resolve(sseResponse("result", {
            itemVersions: [groundedVersion, partialVersion]
        }));
        click(host, "assemble");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /manual note/);
        assert.match(host.innerHTML, /partial-v1/);
        assert.match(host.innerHTML, /来源或事实已变化/);
    });

    it("preserves manual decisions when an item result identity mismatches", async () => {
        const { current, grounded, partial, partialVersion, calls, host, hostRef } = await mountResolvedPartialWithMissingGrounded(372);
        const groundedVersion = itemVersion(grounded.requestKey, "foreign-source", current.evidenceSetVersion, "grounded-v1");
        hostRef.streamResponse = () => Promise.resolve(sseResponse("result", {
            source: current.source,
            sourceVersion: current.sourceVersion,
            evidenceSetVersion: current.evidenceSetVersion,
            itemVersions: [groundedVersion, partialVersion]
        }));
        click(host, "assemble");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /manual note/);
        assert.match(host.innerHTML, /partial-v1/);
        assert.match(host.innerHTML, /生成版本身份无效/);
    });

    it("rejects duplicate response versions for one request in the assembly loop", async () => {
        const { current, grounded, partial, partialVersion, calls, host, hostRef } = await mountResolvedPartialWithMissingGrounded(373);
        const groundedVersion = itemVersion(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "dup-v1");
        hostRef.streamResponse = () => Promise.resolve(sseResponse("result", {
            source: current.source,
            sourceVersion: current.sourceVersion,
            evidenceSetVersion: current.evidenceSetVersion,
            version: groundedVersion,
            itemVersions: [groundedVersion, { ...groundedVersion, versionId: "dup-v2" }]
        }));
        click(host, "assemble");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /manual note/);
        assert.match(host.innerHTML, /partial-v1/);
        assert.match(host.innerHTML, /完整生成返回重复版本/);
    });

    it("routes server stale errors through the stale reset path", async () => {
        const { grounded, partial, calls, host, hostRef, window } = await mountResolvedPartialWithMissingGrounded(375);
        window.confirm = () => false;
        hostRef.streamResponse = () => Promise.resolve(sseResponse("error", {
            code: "TRUST_REPLY_SOURCE_STALE",
            message: "TRUST_REPLY_SOURCE_STALE"
        }));
        const bootstrapCallsBefore = calls.filter((call) => call.url.includes("/bootstrap")).length;
        click(host, "assemble");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.strictEqual(calls.filter((call) => call.url.includes("/bootstrap")).length, bootstrapCallsBefore);
        assert.match(host.innerHTML, /TRUST_REPLY_SOURCE_STALE|来源或事实已变化/);
        assert.doesNotMatch(host.innerHTML, /partial-v1/);
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${partial.requestKey}"[^>]*data-locked="true"`));
        assert.match(host.innerHTML, /manual note/);
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[^>]*data-locked="true"`));
    });

    it("rejects request-disallowed versions for adoption and assembly", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 374;
        const partial = coverageItem(sourceType, sourceId, 0, "PARTIAL");
        const current = bootstrapWithCoverage(sourceType, sourceId, [partial]);
        const liveVersion = {
            ...itemVersion(partial.requestKey, current.sourceVersion, current.evidenceSetVersion, "bad-v1"),
            handling: "ANSWER_WITH_EVIDENCE"
        };
        let generationCalls = 0;
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                generationCalls += 1;
                if (generationCalls === 1) {
                    return Promise.resolve({
                        ok: true,
                        status: 200,
                        json: async () => ({
                            source: current.source,
                            sourceVersion: current.sourceVersion,
                            evidenceSetVersion: current.evidenceSetVersion,
                            version: liveVersion
                        })
                    });
                }
                liveVersion.versionId = "good-v1";
                liveVersion.handling = "ANSWER_SUPPORTED_PART";
                return Promise.resolve({
                    ok: true,
                    status: 200,
                    json: async () => ({
                        source: current.source,
                        sourceVersion: current.sourceVersion,
                        evidenceSetVersion: current.evidenceSetVersion,
                        version: liveVersion
                    })
                });
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
        click(host, "adjust-item", partial.requestKey);
        await settle();
        assert.match(host.innerHTML, /生成版本处理方式无效/);
        click(host, "adjust-item", partial.requestKey);
        await settle();
        liveVersion.handling = "ANSWER_WITH_EVIDENCE";
        click(host, "resolve-item", partial.requestKey);
        await settle();
        assert.match(host.innerHTML, /当前版本无效，请重新生成并采用/);
        click(host, "assemble");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /待人工处理 1 项/);
    });

    it("restores durable locked items on bootstrap without any generation", async () => {
        for (const [mode, sourceType, sourceId] of [["SIMULATION", "TRAINING_MAIL", 380], ["LIVE", "LIVE_INBOUND", 381]]) {
            const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
            const partial = coverageItem(sourceType, sourceId, 1, "PARTIAL", "-partial");
            const current = bootstrapWithCoverage(sourceType, sourceId, [grounded, partial]);
            current.savedState = {
                status: "RESTORED",
                stateVersion: 3,
                selectedModel: "DEEPSEEK_V4_FLASH",
                requestedFactIds: [1],
                lockedItems: [lockedItem(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "restored-v1")]
            };
            const calls = [];
            const { window } = createSandbox((url, options) => {
                calls.push({ url, options });
                if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
                if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
                throw new Error(`unexpected request: ${url}`);
            });
            const host = new FakeElement(window.document);
            window.TrustReplyWorkbench.mount(host, {
                mode,
                source: current.source,
                contextPath: "",
                onComplete: async () => {}
            });
            await settle();
            await settle();
            assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream")).length, 0, mode);
            assert.match(host.innerHTML, /已恢复 1 项已锁定回答/);
            assert.match(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[\\s\\S]*?data-locked="true"`));
            assert.match(host.innerHTML, /已处理/);
            assert.match(host.innerHTML, new RegExp(`data-request-key="${partial.requestKey}"[^>]*data-locked="false"`));
            assert.doesNotMatch(host.innerHTML, /generations\/stream/);
        }
    });

    // 03b (I-4/S-1/S-2): a locked item generated under an older context
    // fingerprint (training knowledge / mail history) is flagged per-item —
    // never dropped — with the verbatim hint; the status area gains the
    // one-click rerun button; clicking it regenerates exactly the affected
    // items through /generations/stream with no re-bootstrap and no
    // resetVersions; untouched items keep their locks.
    it("flags context stale items, renders the rerun button and regenerates exactly the affected items", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 385;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const partial = coverageItem(sourceType, sourceId, 1, "PARTIAL", "-partial");
        const current = bootstrapWithCoverage(sourceType, sourceId, [grounded, partial]);
        current.contextVersion = "ctx-v2";
        current.savedState = {
            status: "RESTORED",
            stateVersion: 1,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [
                lockedItem(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "g-v1", { contextVersion: "ctx-v1" }),
                lockedItem(partial.requestKey, current.sourceVersion, current.evidenceSetVersion, "p-v1", { contextVersion: "ctx-v2", handling: "ANSWER_SUPPORTED_PART" })
            ]
        };
        const calls = [];
        let streamCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/state")) return Promise.resolve(stateResponse(2));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/generations/stream")) {
                streamCount += 1;
                const payload = JSON.parse(options.body);
                const version = {
                    ...itemVersion(payload.requestKey, current.sourceVersion, current.evidenceSetVersion, `fresh-${payload.requestKey}`),
                    contextVersion: "ctx-v2",
                    ...(payload.requestKey === partial.requestKey ? { handling: "ANSWER_SUPPORTED_PART" } : {})
                };
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
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
        await settle();

        // (a) the affected item renders the verbatim per-item hint; the
        // untouched item does not.
        assert.match(host.innerHTML, /<span class="muted" data-role="item-context-stale">本条在旧训练知识\/对话历史下生成<\/span>/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[\\s\\S]*?data-role="item-context-stale"`));
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${partial.requestKey}"[\\s\\S]*?data-role="item-context-stale"`));
        // context staleness never clears the lock.
        assert.match(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.match(host.innerHTML, new RegExp(`data-request-key="${partial.requestKey}"[\\s\\S]*?data-locked="true"`));
        // (b) the status area renders the verbatim one-click rerun button.
        assert.match(host.innerHTML, /<button type="button" class="button small secondary" data-action="regenerate-context-stale">重新生成受影响条目<\/button>/);

        const bootstrapCallsBefore = calls.filter((call) => call.url.includes("/bootstrap")).length;
        click(host, "regenerate-context-stale");
        await settle();
        await settle();

        // (c) exactly the context-stale items are regenerated (1 item → 1
        // ADJUST_ITEM stream call; no FULL_DRAFT).
        assert.strictEqual(streamCount, 1);
        assert.strictEqual(
            calls.filter((call) => call.url.includes("/generations/stream") && JSON.parse(call.options.body).operation === "ADJUST_ITEM").length,
            1
        );
        assert.strictEqual(
            calls.filter((call) => call.url.includes("/generations/stream") && JSON.parse(call.options.body).operation === "FULL_DRAFT").length,
            0
        );
        // (d) no re-bootstrap, no resetVersions: the locks survive and the
        // fresh context fingerprints clear every hint and the button.
        assert.strictEqual(calls.filter((call) => call.url.includes("/bootstrap")).length, bootstrapCallsBefore);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.match(host.innerHTML, new RegExp(`data-request-key="${partial.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.doesNotMatch(host.innerHTML, /data-role="item-context-stale"/);
        assert.doesNotMatch(host.innerHTML, /data-action="regenerate-context-stale"/);
    });

    it("does not restore stale saved state and never generates", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 382;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithCoverage(sourceType, sourceId, [grounded]);
        current.savedState = {
            status: "STALE",
            stateVersion: 2,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [lockedItem(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "stale-v1")]
        };
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream")).length, 0);
        assert.match(host.innerHTML, /STALE：来源或依据已变化，旧锁定回答未恢复/);
        assert.doesNotMatch(host.innerHTML, /stale-v1/);
        assert.doesNotMatch(host.innerHTML, /data-locked="true"/);
    });

    it("rolls back a failed durable lock and keeps the active version adoptable", async () => {
        const current = bootstrap("LIVE_INBOUND", 383);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "adopt-v1");
        let stateCalls = 0;
        const { window } = createSandbox((url, options) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCalls += 1;
                return stateCalls === 1 ? Promise.resolve(conflictResponse()) : Promise.resolve(stateResponse(1));
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
        await settle();
        click(host, "adjust-item", requestKey);
        await settle();
        click(host, "resolve-item", requestKey);
        await settle();
        assert.doesNotMatch(host.innerHTML, /data-locked="true"/);
        assert.match(host.innerHTML, /TRUST_REPLY_STATE_CONFLICT|保存失败/);
        assert.match(host.innerHTML, /采用此版本/);
        click(host, "resolve-item", requestKey);
        await settle();
        assert.match(host.innerHTML, /data-locked="true"/);
        assert.strictEqual(stateCalls, 2);
    });

    it("deletes saved state before switching facts and refuses to switch on delete failure", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 384;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithCoverage(sourceType, sourceId, [grounded]);
        current.savedState = {
            status: "RESTORED",
            stateVersion: 4,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [lockedItem(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "r-v1")]
        };
        const buildMount = (failDelete) => {
            const statePayloads = [];
            let bootstrapCalls = 0;
            const { window } = createSandbox((url, options) => {
                if (url.includes("/bootstrap")) {
                    bootstrapCalls += 1;
                    return Promise.resolve(jsonResponse(current));
                }
                if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
                if (url.includes("/state")) {
                    statePayloads.push(JSON.parse(options.body));
                    return failDelete
                        ? Promise.resolve({ ok: false, status: 500, json: async () => ({ code: "TRUST_REPLY_STATE_TOO_LARGE" }) })
                        : Promise.resolve(stateResponse(0));
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
            return { window, host, statePayloads, getBootstrapCalls: () => bootstrapCalls };
        };

        const okMount = buildMount(false);
        await settle();
        await settle();
        click(okMount.host, "add-fact", grounded.requestKey, undefined, "2");
        await settle();
        await settle();
        assert.strictEqual(okMount.statePayloads.length, 1);
        assert.deepStrictEqual(okMount.statePayloads[0].lockedItems, []);
        assert.strictEqual(okMount.statePayloads[0].expectedStateVersion, 4);
        assert.strictEqual(okMount.statePayloads[0].schemaVersion, "trust-reply-workbench-state-v3");
        assert.deepStrictEqual(okMount.statePayloads[0].requestFactSelections[0].factRuleIds, [1], "delete must use the old matrix");
        assert.ok(okMount.getBootstrapCalls() >= 2, "facts must re-bootstrap after a successful delete");

        const failMount = buildMount(true);
        await settle();
        await settle();
        click(failMount.host, "add-fact", grounded.requestKey, undefined, "2");
        await settle();
        assert.strictEqual(failMount.statePayloads.length, 1);
        assert.strictEqual(failMount.getBootstrapCalls(), 1, "delete failure must not switch facts");
        assert.match(failMount.host.innerHTML, /旧锁定状态删除失败/);
    });

    // 03a (I-5): a fact change re-bootstraps exactly once and resets only the
    // affected item; untouched items keep their locked answers and the stale
    // hint (S-1) appears only on the changed card. No full-screen skeleton.
    it("preserves untouched items and resets only the changed item after fact edits", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 396;
        const first = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const second = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-second");
        const current = bootstrapWithCoverage(sourceType, sourceId, [first, second]);
        current.savedState = {
            status: "RESTORED",
            stateVersion: 3,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [
                lockedItem(first.requestKey, current.sourceVersion, current.evidenceSetVersion, "first-v1"),
                lockedItem(second.requestKey, current.sourceVersion, current.evidenceSetVersion, "second-v1")
            ]
        };
        const changed = bootstrapWithCoverage(sourceType, sourceId, [
            { ...first, evidenceSetVersion: "changed-e1" },
            second
        ]);
        const calls = [];
        const confirmCalls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) {
                const bootCalls = calls.filter((call) => call.url.includes("/bootstrap")).length;
                return Promise.resolve(jsonResponse(bootCalls === 1 ? current : changed));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            throw new Error(`unexpected request: ${url}`);
        });
        window.confirm = (message) => {
            confirmCalls.push(message);
            return true;
        };
        const host = new FakeElement(window.document);
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        await settle();

        const bootstrapCallsBefore = calls.filter((call) => call.url.includes("/bootstrap")).length;
        click(host, "add-fact", first.requestKey, undefined, "2");
        await settle();
        await settle();

        // I-5: the fact edit triggers exactly one new /bootstrap.
        assert.strictEqual(calls.filter((call) => call.url.includes("/bootstrap")).length, bootstrapCallsBefore + 1);
        // The untouched item keeps its locked answer.
        assert.match(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[\\s\\S]*?second-v1`));
        assert.match(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[\\s\\S]*?data-locked="true"`));
        // The changed item lost its answer and shows the verbatim S-1 hint.
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${first.requestKey}"[\\s\\S]*?first-v1`));
        assert.match(
            host.innerHTML,
            /<span class="muted" data-role="item-evidence-stale">事实已变化，本条回答需重新生成<\/span>/
        );
        // The scoped confirmation asked exactly once, naming only this item.
        assert.strictEqual(confirmCalls.length, 1);
        assert.match(confirmCalls[0], /本条/);
        assert.match(confirmCalls[0], /其余摘要保留/);
        // Observable outcome 2: never the full-screen loading skeleton.
        assert.doesNotMatch(host.innerHTML, /正在加载工作台/);
        instance.unmount();
    });

    // 03a (T7 / A-3): the confirmation only appears when the changed item
    // itself has a generated or locked answer; empty items change silently.
    it("confirms fact changes only when the changed item has a generated answer", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 398;
        const first = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const second = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-second");
        const current = bootstrapWithCoverage(sourceType, sourceId, [first, second]);
        current.savedState = {
            status: "RESTORED",
            stateVersion: 2,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [lockedItem(first.requestKey, current.sourceVersion, current.evidenceSetVersion, "first-v1")]
        };
        const confirmCalls = [];
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            throw new Error(`unexpected request: ${url}`);
        });
        window.confirm = (message) => {
            confirmCalls.push(message);
            return true;
        };
        const host = new FakeElement(window.document);
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        await settle();

        // item 2 has no answer: no confirmation.
        click(host, "add-fact", second.requestKey, undefined, "2");
        await settle();
        await settle();
        assert.strictEqual(confirmCalls.length, 0);

        // item 1 has a locked answer: confirmation with the scoped wording.
        click(host, "add-fact", first.requestKey, undefined, "3");
        await settle();
        await settle();
        assert.strictEqual(confirmCalls.length, 1);
        assert.match(confirmCalls[0], /该摘要的事实变化会清空本条已生成回答，其余摘要保留，继续？/);
        instance.unmount();
    });

    // 03a (I-4): PARTIALLY_RESTORED restores the kept locks and reports the
    // dropped count instead of wiping everything.
    it("restores kept locks and reports dropped count for partially restored saved state", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 397;
        const first = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const second = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-second");
        const current = bootstrapWithCoverage(sourceType, sourceId, [first, second]);
        current.savedState = {
            status: "PARTIALLY_RESTORED",
            stateVersion: 4,
            droppedItemCount: 1,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [lockedItem(first.requestKey, current.sourceVersion, current.evidenceSetVersion, "kept-v1")]
        };
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        await settle();

        assert.match(host.innerHTML, /kept-v1/);
        assert.match(host.innerHTML, /PARTIALLY_RESTORED/);
        assert.match(host.innerHTML, /丢弃 1 项/);
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[\\s\\S]*?data-locked="true"`));
        instance.unmount();
    });

    it("generates all missing grounded items in canonical order with durable per-item saves", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 385;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-b");
        const g2 = coverageItem(sourceType, sourceId, 2, "GROUNDED", "-c");
        const partial = coverageItem(sourceType, sourceId, 3, "PARTIAL", "-partial");
        const current = bootstrapWithCoverage(sourceType, sourceId, [g0, g1, g2, partial]);
        const versions = {
            [g0.requestKey]: itemVersion(g0.requestKey, current.sourceVersion, current.evidenceSetVersion, "g0-v1"),
            [g1.requestKey]: itemVersion(g1.requestKey, current.sourceVersion, current.evidenceSetVersion, "g1-v1"),
            [g2.requestKey]: itemVersion(g2.requestKey, current.sourceVersion, current.evidenceSetVersion, "g2-v1")
        };
        const calls = [];
        const streamPayloads = [];
        let stateCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamPayloads.push(payload);
                const version = versions[payload.requestKey]
                    || {
                        ...itemVersion(payload.requestKey, current.sourceVersion, current.evidenceSetVersion, "partial-v1"),
                        handling: "ANSWER_SUPPORTED_PART"
                    };
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return Promise.resolve(stateResponse(stateCount));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "looped draft",
                    renderedDraftText: "looped draft",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [versions[g0.requestKey], versions[g1.requestKey], versions[g2.requestKey]]
                }));
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
        click(host, "adjust-item", partial.requestKey);
        await settle();
        click(host, "resolve-item", partial.requestKey);
        await settle();
        click(host, "assemble");
        await settle();

        const groundedPayloads = streamPayloads.filter((payload) => payload.requestKey !== partial.requestKey);
        assert.strictEqual(groundedPayloads.length, 3);
        assert.deepStrictEqual(groundedPayloads.map((payload) => payload.requestKey), [g0.requestKey, g1.requestKey, g2.requestKey]);
        assert.ok(groundedPayloads.every((payload) => payload.operation === "ADJUST_ITEM"));
        assert.ok(groundedPayloads.every((payload) => payload.handling === "ANSWER_WITH_EVIDENCE"));
        assert.strictEqual(streamPayloads.filter((payload) => payload.requestKey === partial.requestKey).length, 1);
        assert.strictEqual(streamPayloads.filter((payload) => payload.operation === "FULL_DRAFT").length, 0);
        assert.strictEqual(stateCount, 4);
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 1);
        assert.match(host.innerHTML, /looped draft/);
        assert.match(host.innerHTML, /g0-v1/);
    });

    it("stops the assembly loop at the k-th failure and keeps earlier durable items", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 386;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-b");
        const g2 = coverageItem(sourceType, sourceId, 2, "GROUNDED", "-c");
        const current = bootstrapWithCoverage(sourceType, sourceId, [g0, g1, g2]);
        const versions = {
            [g0.requestKey]: itemVersion(g0.requestKey, current.sourceVersion, current.evidenceSetVersion, "g0-v1"),
            [g1.requestKey]: itemVersion(g1.requestKey, current.sourceVersion, current.evidenceSetVersion, "g1-v1"),
            [g2.requestKey]: itemVersion(g2.requestKey, current.sourceVersion, current.evidenceSetVersion, "g2-v1")
        };
        const calls = [];
        let streamCount = 0;
        let stateCount = 0;
        let failNext = false;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamCount += 1;
                if (payload.requestKey === g1.requestKey && failNext) {
                    return Promise.resolve(sseResponse("error", { message: "k-th failed" }));
                }
                const version = versions[payload.requestKey];
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return Promise.resolve(stateResponse(stateCount));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "retried draft",
                    renderedDraftText: "retried draft",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [versions[g0.requestKey], versions[g1.requestKey], versions[g2.requestKey]]
                }));
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
        failNext = true;
        click(host, "assemble");
        await settle();
        assert.strictEqual(streamCount, 2);
        assert.strictEqual(stateCount, 1);
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /k-th failed/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${g0.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.doesNotMatch(host.innerHTML, /g2-v1/);
        failNext = false;
        click(host, "assemble");
        await settle();
        assert.strictEqual(streamCount, 4);
        assert.strictEqual(stateCount, 3);
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 1);
        assert.match(host.innerHTML, /retried draft/);
        assert.match(host.innerHTML, /g2-v1/);
    });

    it("stops the assembly loop when a durable save conflicts", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 387;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-b");
        const current = bootstrapWithCoverage(sourceType, sourceId, [g0, g1]);
        const versions = {
            [g0.requestKey]: itemVersion(g0.requestKey, current.sourceVersion, current.evidenceSetVersion, "g0-v1"),
            [g1.requestKey]: itemVersion(g1.requestKey, current.sourceVersion, current.evidenceSetVersion, "g1-v1")
        };
        const calls = [];
        let streamCount = 0;
        let stateCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamCount += 1;
                const version = versions[payload.requestKey];
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return stateCount === 2 ? Promise.resolve(conflictResponse()) : Promise.resolve(stateResponse(stateCount));
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
        click(host, "assemble");
        await settle();
        assert.strictEqual(streamCount, 2);
        assert.strictEqual(stateCount, 2);
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /TRUST_REPLY_STATE_CONFLICT|保存失败/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${g0.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${g1.requestKey}"[^>]*data-locked="true"`));
    });

    it("cancels only the current generation in the assembly sequence", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 388;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-b");
        const current = bootstrapWithCoverage(sourceType, sourceId, [g0, g1]);
        const v0 = itemVersion(g0.requestKey, current.sourceVersion, current.evidenceSetVersion, "g0-v1");
        const calls = [];
        let streamCount = 0;
        let stateCount = 0;
        const pending = pendingSseFetch();
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamCount += 1;
                if (payload.requestKey === g0.requestKey) {
                    return Promise.resolve(sseResponse("result", {
                        source: current.source,
                        sourceVersion: current.sourceVersion,
                        evidenceSetVersion: current.evidenceSetVersion,
                        version: v0
                    }));
                }
                pending.bind(options);
                return pending.promise;
            }
            if (url.includes("/cancel")) return Promise.resolve(jsonResponse({ status: "CANCELLED" }));
            if (url.includes("/state")) {
                stateCount += 1;
                return Promise.resolve(stateResponse(stateCount));
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
        click(host, "assemble");
        await settle();
        assert.strictEqual(streamCount, 2);
        assert.strictEqual(stateCount, 1);
        click(host, "cancel-generation");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, /已取消生成，可重试/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${g0.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.doesNotMatch(host.innerHTML, /g1-v1/);
    });

    it("stops assembly after cancellation during a durable item save", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 389;
        const g0 = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const g1 = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-b");
        const current = bootstrapWithCoverage(sourceType, sourceId, [g0, g1]);
        const versions = {
            [g0.requestKey]: itemVersion(g0.requestKey, current.sourceVersion, current.evidenceSetVersion, "g0-v1"),
            [g1.requestKey]: itemVersion(g1.requestKey, current.sourceVersion, current.evidenceSetVersion, "g1-v1")
        };
        const calls = [];
        let streamCount = 0;
        let stateCount = 0;
        const pendingSave = deferred();
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                streamCount += 1;
                const version = versions[payload.requestKey];
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return stateCount === 1 ? pendingSave.promise : Promise.resolve(stateResponse(stateCount));
            }
            if (url.includes("/cancel")) return Promise.resolve(jsonResponse({ status: "CANCELLED" }));
            if (url.includes("/assemble")) return Promise.resolve(jsonResponse({}));
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
        assert.strictEqual(streamCount, 1);
        assert.strictEqual(stateCount, 1);

        click(host, "cancel-generation");
        await settle();
        assert.match(host.innerHTML, /已取消生成，可重试/);

        pendingSave.resolve(stateResponse(1));
        await settle();
        await settle();
        assert.strictEqual(streamCount, 1);
        assert.strictEqual(calls.filter((call) => call.url.includes("/assemble")).length, 0);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${g0.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.doesNotMatch(host.innerHTML, /g1-v1/);
    });

    it("renders two equal tabs with unique panel ids and switches pages without re-bootstrap", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 500;
        const current = bootstrap(sourceType, sourceId);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();
        const bootstrapCalls = calls.filter((call) => call.url.includes("/bootstrap")).length;
        assert.match(host.innerHTML, /role="tablist"/);
        assert.strictEqual((host.innerHTML.match(/role="tab"/g) || []).length, 2);
        assert.match(host.innerHTML, /data-page-panel="facts"/);
        assert.match(host.innerHTML, /data-page-panel="frame"[^>]* hidden/);
        assert.strictEqual((host.innerHTML.match(/aria-selected="true"/g) || []).length, 1);
        const factsPanelId = host.innerHTML.match(/data-page-panel="facts" id="([^"]+)"/)?.[1];
        const framePanelId = host.innerHTML.match(/data-page-panel="frame" id="([^"]+)"/)?.[1];
        assert.ok(factsPanelId && framePanelId && factsPanelId !== framePanelId, "panel ids must be instance-unique");
        assert.ok(host.innerHTML.includes(`aria-controls="${framePanelId}"`));

        click(host, "next-page");
        assert.match(host.innerHTML, /data-page-panel="facts"[^>]* hidden/);
        assert.doesNotMatch(host.innerHTML, /data-page-panel="frame"[^>]* hidden/);
        assert.match(host.innerHTML, /data-action="prev-page"/);
        click(host, "prev-page");
        assert.doesNotMatch(host.innerHTML, /data-page-panel="facts"[^>]* hidden/);
        assert.match(host.innerHTML, /data-page-panel="frame"[^>]* hidden/);
        click(host, "set-page", undefined, undefined, undefined, "frame");
        assert.match(host.innerHTML, /data-page-panel="facts"[^>]* hidden/);
        assert.strictEqual(
            calls.filter((call) => call.url.includes("/bootstrap")).length,
            bootstrapCalls,
            "page switching must never re-bootstrap"
        );
    });

    it("navigates the two tabs with arrow and home/end keys", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 501;
        const current = bootstrap(sourceType, sourceId);
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();

        const factsTab = { dataset: { page: "facts" } };
        const frameTab = { dataset: { page: "frame" } };
        host.querySelectorAll = (selector) => (selector === '[role="tab"]' ? [factsTab, frameTab] : []);
        const dispatchKey = (key, tab) => host.dispatchEvent("keydown", {
            key,
            closest: () => tab,
            preventDefault: () => {}
        });

        dispatchKey("ArrowRight", factsTab);
        assert.match(host.innerHTML, /data-page-panel="facts"[^>]* hidden/, "ArrowRight must open the frame page");
        dispatchKey("ArrowLeft", frameTab);
        assert.doesNotMatch(host.innerHTML, /data-page-panel="facts"[^>]* hidden/, "ArrowLeft must open the facts page");
        dispatchKey("End", factsTab);
        assert.match(host.innerHTML, /data-page-panel="facts"[^>]* hidden/, "End must open the last page");
        dispatchKey("Home", frameTab);
        assert.doesNotMatch(host.innerHTML, /data-page-panel="facts"[^>]* hidden/, "Home must open the first page");
    });

    it("focuses the target tab via role/data attributes, never a bare instanceId id selector", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 503;
        const current = bootstrap(sourceType, sourceId);
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();

        // I-1/I-3/I-4: swap in a spy so the real selector text setActivePage
        // builds is observable — the FakeElement stub never validates selector
        // syntax (A-7 blindness) and its focus() would be silently lost.
        const selectors = [];
        let focusCalls = 0;
        const focusable = { focus: () => { focusCalls += 1; } };
        host.querySelector = (selector) => {
            selectors.push(selector);
            return /^\[role="tab"\]\[data-page="(facts|frame)"\]$/.test(selector) ? focusable : null;
        };

        click(host, "set-page", undefined, undefined, undefined, "frame");
        await settle();

        assert.ok(selectors.length > 0, "setActivePage must call host.querySelector");
        const tabSelector = selectors.find((s) => s.includes("data-page"));
        assert.ok(tabSelector, "a tab selector must be built");
        assert.ok(!tabSelector.startsWith("#"), "selector must not be a bare instanceId id selector");
        assert.match(tabSelector, /^\[role="tab"\]\[data-page="(facts|frame)"\]$/, "selector must be unique to the tab button");
        assert.strictEqual(focusCalls, 1, "focus must land on the target tab exactly once");

        // I-1 source-text guard: the component must never build bare
        // `querySelector(`#${...})` selectors again (K-dom-stub-tests-hide-dangling-refs).
        assert.ok(!source.includes("querySelector(`#${"), "component must not contain bare template-literal id selectors");
        assert.ok(source.includes('[role="tab"][data-page="'), "component must query tabs by role + data-page");
    });

    it("keeps a cross-request fact selectable in the picker and releases facts on remove", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 502;
        const first = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const second = coverageItem(sourceType, sourceId, 1, "GROUNDED", "-second");
        const current = bootstrapWithCoverage(sourceType, sourceId, [first, second]);
        current.rulesByCategory = [
            { ruleId: 1, displayName: "Fact One", answerBody: "answer one" },
            { ruleId: 2, displayName: "Fact Two", answerBody: "answer two" },
            { ruleId: 3, displayName: "Fact Three", answerBody: "answer three" }
        ];
        current.requestCoverage[0].factRuleIds = [1];
        current.requestCoverage[1].factRuleIds = [];
        current.requestFactSelections = [
            { requestKey: first.requestKey, factRuleIds: [1] },
            { requestKey: second.requestKey, factRuleIds: [] }
        ];
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();

        assert.match(host.innerHTML, new RegExp(`data-request-key="${first.requestKey}"[\\s\\S]*?class="trust-reply-fact-chip"`));
        click(host, "toggle-fact-picker", second.requestKey);
        // 计划 02 (I-6): 同一 fact 可绑定多个 request；已绑定 request A 的
        // fact 在 request B 的 picker 中仍显示「可添加」、可选，不产生
        // used 状态、无「已用于摘要 N」提示、无 disabled 选项。
        assert.match(host.innerHTML, /data-fact-id="1"[^>]*data-state="available"/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[\\s\\S]*?data-fact-id="1"[^>]*data-state="available"`));
        assert.doesNotMatch(host.innerHTML, /data-state="used"/);
        assert.doesNotMatch(host.innerHTML, /已用于摘要 1/);
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[^>]*data-fact-id="1"[^>]*disabled`));
        assert.doesNotMatch(host.innerHTML, new RegExp(`data-request-key="${second.requestKey}"[^>]*data-state="selected"`));

        click(host, "remove-fact", first.requestKey, undefined, "1");
        await settle();
        await settle();
        const bootstrapPayloads = calls
            .filter((call) => call.url.includes("/bootstrap"))
            .map((call) => JSON.parse(call.options.body));
        assert.ok(bootstrapPayloads.length >= 2, "remove must re-bootstrap");
        const lastMatrix = bootstrapPayloads.at(-1).requestFactSelections.find((s) => s.requestKey === first.requestKey);
        assert.deepStrictEqual(lastMatrix.factRuleIds, [], "the released fact must leave the matrix");
    });

    it("renders fact head with count and filters picker options via search input", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 512;
        const first = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithCoverage(sourceType, sourceId, [first]);
        current.rulesByCategory = [
            { ruleId: 1, displayName: "Fact One", answerBody: "answer one" },
            { ruleId: 2, displayName: "Fact Two", answerBody: "answer two" }
        ];
        current.requestCoverage[0].factRuleIds = [1];
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();

        assert.match(host.innerHTML, /class="trust-reply-fact-head"/);
        assert.match(host.innerHTML, /class="trust-reply-fact-count">1</);
        assert.match(host.innerHTML, /data-role="fact-search"/);
        assert.match(host.innerHTML, /data-search="fact two answer two"/);
        assert.match(host.innerHTML, /class="trust-reply-fact-state" data-state="selected"/);

        const optA = { dataset: { search: "fact one answer one" }, hidden: false };
        const optB = { dataset: { search: "fact two answer two" }, hidden: false };
        const picker = { querySelectorAll: (selector) => selector === ".trust-reply-fact-picker-option" ? [optA, optB] : [] };
        const target = {
            dataset: { role: "fact-search", requestKey: first.requestKey },
            value: "two",
            closest: (selector) => selector === '[data-role="fact-picker"]' ? picker : null
        };
        host.dispatchEvent("input", target);
        assert.strictEqual(optA.hidden, true, "non-matching option must hide");
        assert.strictEqual(optB.hidden, false, "matching option must stay visible");
        target.value = "";
        host.dispatchEvent("input", target);
        assert.strictEqual(optA.hidden, false, "clearing the query must restore options");
    });

    it("cancels a destructive fact change without touching state or DOM", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 507;
        const current = bootstrap(sourceType, sourceId);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "locked-v1");
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/state")) return Promise.resolve(stateResponse(1));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        click(host, "adjust-item", requestKey);
        await settle();
        click(host, "resolve-item", requestKey);
        await settle();
        assert.match(host.innerHTML, /data-locked="true"/);
        const stateCallsBefore = calls.filter((call) => call.url.includes("/state")).length;

        window.confirm = () => false;
        click(host, "add-fact", requestKey, undefined, "2");
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/state")).length, stateCallsBefore, "cancel must not delete state");
        assert.strictEqual(calls.filter((call) => call.url.includes("/bootstrap")).length, 1, "cancel must not re-bootstrap");
        assert.match(host.innerHTML, /data-locked="true"/);
    });

    it("confirms a destructive fact change, deletes durable state, resets the changed item and re-bootstraps", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 508;
        const current = bootstrap(sourceType, sourceId);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "locked-v1");
        const calls = [];
        let stateCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) {
                // 03a (I-5): the re-bootstrap reflects the changed fact
                // assignment with a drifted per-request evidence version.
                const bootCalls = calls.filter((call) => call.url.includes("/bootstrap")).length;
                if (bootCalls === 1) return Promise.resolve(jsonResponse(current));
                const changed = bootstrap(sourceType, sourceId);
                changed.requestCoverage[0].evidenceSetVersion = "changed-e1";
                return Promise.resolve(jsonResponse(changed));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return Promise.resolve(stateResponse(stateCount));
            }
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        click(host, "adjust-item", requestKey);
        await settle();
        click(host, "resolve-item", requestKey);
        await settle();
        assert.match(host.innerHTML, /data-locked="true"/);

        click(host, "add-fact", requestKey, undefined, "2");
        await settle();
        await settle();
        const statePayloads = calls.filter((call) => call.url.includes("/state")).map((call) => JSON.parse(call.options.body));
        assert.deepStrictEqual(statePayloads.at(-1).lockedItems, [], "durable state must be deleted first");
        assert.strictEqual(statePayloads.at(-1).expectedStateVersion, 1);
        assert.ok(calls.filter((call) => call.url.includes("/bootstrap")).length >= 2, "fact change must re-bootstrap");
        assert.doesNotMatch(host.innerHTML, /data-locked="true"/, "the changed item's lock must not survive the fact change");
        assert.match(host.innerHTML, /待生成/);
    });

    it("frame change clears only the assembly, keeps locks and persists the new frame", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 503;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithFrame(sourceType, sourceId, [grounded]);
        const requestKey = grounded.requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "fv-locked");
        const calls = [];
        let stateCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return Promise.resolve(frameStateResponse(stateCount, {
                    selection: { salutationSnippetId: 11, greetingSnippetId: 22, ackSnippetId: null, closingSnippetId: 41 },
                    version: "frame-v2"
                }, [serializeLocked(version, current)]));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "frame server draft",
                    renderedDraftText: "frame server draft",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [version],
                    requestFactSelections: [{ requestKey, factRuleIds: [1] }],
                    frameSnapshot: {
                        selection: { ...current.frameSnapshot.selection },
                        version: current.frameSnapshot.version
                    }
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        click(host, "adjust-item", requestKey);
        await settle();
        click(host, "resolve-item", requestKey);
        await settle();
        click(host, "assemble");
        await settle();
        assert.match(host.innerHTML, /服务端整合完成/);
        assert.match(host.innerHTML, /frame server draft/);
        assert.doesNotMatch(host.innerHTML, /data-action="complete" disabled/);

        host.dispatchEvent("change", { dataset: { role: "frame-select", frameSlot: "greetingSnippetId" }, value: "22" });
        await settle();
        await settle();
        assert.doesNotMatch(host.innerHTML, /data-state="CURRENT"/, "a frame change must invalidate the assembly");
        assert.match(host.innerHTML, /data-state="STALE"/);
        assert.match(host.innerHTML, /配置已变化 · 请重新整合/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${requestKey}"[\\s\\S]*?data-locked="true"`), "locks must survive a frame change");
        const statePayloads = calls.filter((call) => call.url.includes("/state")).map((call) => JSON.parse(call.options.body));
        const frameSave = statePayloads.at(-1);
        assert.strictEqual(frameSave.frameSnapshot.selection.greetingSnippetId, 22);
        assert.strictEqual(frameSave.frameSnapshot.selection.salutationSnippetId, 11, "other slots must be preserved");
        assert.deepStrictEqual(frameSave.lockedItems.map((item) => item.requestKey), [requestKey], "the same locked items must be saved");
    });

    it("keeps locked answers and switches to the frame page on a frame stale conflict", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 509;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithFrame(sourceType, sourceId, [grounded]);
        const requestKey = grounded.requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "stale-frame-locked");
        let stateCount = 0;
        const { window } = createSandbox((url, options) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return stateCount >= 2
                    ? Promise.resolve(conflictResponse("TRUST_REPLY_FRAME_STALE"))
                    : Promise.resolve(frameStateResponse(stateCount, current.frameSnapshot, [serializeLocked(version, current)]));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "stale draft",
                    renderedDraftText: "stale draft",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [version],
                    frameSnapshot: { selection: { ...current.frameSnapshot.selection }, version: current.frameSnapshot.version }
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        click(host, "adjust-item", requestKey);
        await settle();
        click(host, "resolve-item", requestKey);
        await settle();

        host.dispatchEvent("change", { dataset: { role: "frame-select", frameSlot: "greetingSnippetId" }, value: "22" });
        await settle();
        await settle();
        assert.match(host.innerHTML, /TRUST_REPLY_FRAME_STALE|框架配置已变化/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${requestKey}"[\\s\\S]*?data-locked="true"`), "locks must survive frame stale");
        assert.doesNotMatch(host.innerHTML, /data-page-panel="frame"[^>]* hidden/, "frame page must be active after frame stale");
        assert.match(host.innerHTML, /data-page-panel="facts"[^>]* hidden/);
    });

    it("sends the full matrix and frame snapshot on every generation, state and assemble payload", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 504;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const partial = coverageItem(sourceType, sourceId, 1, "PARTIAL", "-partial");
        const current = bootstrapWithFrame(sourceType, sourceId, [grounded, partial]);
        const partialVersion = {
            ...itemVersion(partial.requestKey, current.sourceVersion, current.evidenceSetVersion, "partial-v1"),
            handling: "ANSWER_SUPPORTED_PART"
        };
        const groundedVersion = itemVersion(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "grounded-v1");
        const calls = [];
        let stateCount = 0;
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                const payload = JSON.parse(options.body);
                const version = payload.requestKey === partial.requestKey ? partialVersion : groundedVersion;
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/state")) {
                stateCount += 1;
                return Promise.resolve(frameStateResponse(stateCount, current.frameSnapshot, []));
            }
            if (url.includes("/assemble")) {
                return Promise.resolve(jsonResponse({
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    rawDraftText: "matrix draft",
                    renderedDraftText: "matrix draft",
                    draftHash: "hash",
                    canonicalFactIds: [1],
                    itemVersions: [groundedVersion, partialVersion],
                    requestFactSelections: [
                        { requestKey: grounded.requestKey, factRuleIds: [1] },
                        { requestKey: partial.requestKey, factRuleIds: [1] }
                    ],
                    frameSnapshot: { selection: { ...current.frameSnapshot.selection }, version: current.frameSnapshot.version }
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        click(host, "adjust-item", partial.requestKey);
        await settle();
        click(host, "resolve-item", partial.requestKey);
        await settle();
        click(host, "assemble");
        await settle();

        const payloads = calls.map((call) => JSON.parse(call.options.body));
        const expectedMatrix = [
            { requestKey: grounded.requestKey, factRuleIds: [1] },
            { requestKey: partial.requestKey, factRuleIds: [1] }
        ];
        payloads.forEach((payload) => {
            assert.ok(!("requestedFactIds" in payload), "no payload may carry flat requestedFactIds");
        });
        const statePayloads = payloads.filter((payload) => payload.schemaVersion);
        assert.ok(statePayloads.length >= 2);
        statePayloads.forEach((payload) => {
            assert.strictEqual(payload.schemaVersion, "trust-reply-workbench-state-v3");
            assert.deepStrictEqual(payload.requestFactSelections, expectedMatrix);
            assert.deepStrictEqual(payload.frameSnapshot.selection, current.frameSnapshot.selection);
        });
        const streamPayloads = payloads.filter((payload) => payload.operation === "ADJUST_ITEM");
        assert.strictEqual(streamPayloads.length, 2);
        streamPayloads.forEach((payload) => {
            assert.deepStrictEqual(payload.requestFactSelections, expectedMatrix);
        });
        const assemblePayload = payloads.find((payload) => payload.lockedItems && !payload.schemaVersion && !payload.operation);
        assert.ok(assemblePayload, "assemble payload must exist");
        assert.deepStrictEqual(assemblePayload.requestFactSelections, expectedMatrix);
        assert.deepStrictEqual(assemblePayload.frameSnapshot.selection, current.frameSnapshot.selection);
        assert.match(host.innerHTML, /matrix draft/);
    });

    it("fails closed when the server canonical matrix disagrees with coverage", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 505;
        const current = bootstrap(sourceType, sourceId);
        current.requestFactSelections = [{ requestKey: current.requestCoverage[0].requestKey, factRuleIds: [9] }];
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();
        assert.match(host.innerHTML, /TRUST_REPLY_FACT_SELECTION_INVALID/);
    });

    it("derives the local preview from resolved versions only", async () => {
        const sourceType = "LIVE_INBOUND";
        const sourceId = 506;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithFrame(sourceType, sourceId, [grounded]);
        const requestKey = grounded.requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "preview-v1");
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/state")) return Promise.resolve(frameStateResponse(1, current.frameSnapshot, [serializeLocked(version, current)]));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    version
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        click(host, "adjust-item", requestKey);
        await settle();
        let preview = host.innerHTML.match(/<pre class="pre" data-role="local-preview">([\s\S]*?)<\/pre>/)?.[1] || "";
        assert.doesNotMatch(preview, /answer/, "an active, unadopted version must not enter the local preview");
        assert.match(host.innerHTML, /配置预览 · 尚未服务端整合/);

        click(host, "resolve-item", requestKey);
        await settle();
        preview = host.innerHTML.match(/<pre class="pre" data-role="local-preview">([\s\S]*?)<\/pre>/)?.[1] || "";
        assert.match(preview, /answer/, "the resolved version must enter the local preview");
    });

    it("isolates instance ids, active pages and state across two mounts", async () => {
        const pendingTraining = deferred();
        const pendingLive = deferred();
        let uuidCounter = 0;
        const { window } = createSandbox((url, options) => {
            const request = JSON.parse(options.body);
            return request.source.sourceType === "TRAINING_MAIL" ? pendingTraining.promise : pendingLive.promise;
        }, { crypto: { randomUUID: () => `00000000-0000-4000-8000-${String(++uuidCounter).padStart(12, "0")}` } });
        const trainingHost = new FakeElement(window.document);
        const liveHost = new FakeElement(window.document);
        const training = window.TrustReplyWorkbench.mount(trainingHost, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
            contextPath: "",
            onComplete: async () => {}
        });
        const live = window.TrustReplyWorkbench.mount(liveHost, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 602 },
            contextPath: "",
            onComplete: async () => {}
        });
        pendingTraining.resolve({ ok: true, status: 200, json: async () => bootstrap("TRAINING_MAIL", 601) });
        pendingLive.resolve({ ok: true, status: 200, json: async () => bootstrap("LIVE_INBOUND", 602) });
        await new Promise((resolve) => setImmediate(resolve));

        const trainingFactsId = trainingHost.innerHTML.match(/data-page-panel="facts" id="([^"]+)"/)?.[1];
        const liveFactsId = liveHost.innerHTML.match(/data-page-panel="facts" id="([^"]+)"/)?.[1];
        assert.ok(trainingFactsId && liveFactsId, "both mounts must render panels");
        assert.notStrictEqual(trainingFactsId, liveFactsId, "panel ids must be per-instance");

        click(trainingHost, "next-page");
        assert.match(trainingHost.innerHTML, /data-page-panel="facts"[^>]* hidden/);
        assert.doesNotMatch(liveHost.innerHTML, /data-page-panel="facts"[^>]* hidden/, "live mount must keep its own page");
        assert.match(liveHost.innerHTML, /data-page-panel="frame"[^>]* hidden/);

        training.unmount();
        live.unmount();
        assert.strictEqual(trainingHost.innerHTML, "");
        assert.strictEqual(liveHost.innerHTML, "");
    });

    it("restores locked items on a FRAME_STALE saved state and opens the frame page", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 511;
        const grounded = coverageItem(sourceType, sourceId, 0, "GROUNDED");
        const current = bootstrapWithFrame(sourceType, sourceId, [grounded]);
        current.savedState = {
            status: "FRAME_STALE",
            stateVersion: 3,
            selectedModel: "DEEPSEEK_V4_FLASH",
            requestedFactIds: [1],
            lockedItems: [lockedItem(grounded.requestKey, current.sourceVersion, current.evidenceSetVersion, "restored-frame-v1")]
        };
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
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
        await settle();
        assert.strictEqual(calls.filter((call) => call.url.includes("/generations/stream")).length, 0);
        assert.match(host.innerHTML, /FRAME_STALE：框架配置已变化/);
        assert.match(host.innerHTML, new RegExp(`data-request-key="${grounded.requestKey}"[\\s\\S]*?data-locked="true"`));
        assert.doesNotMatch(host.innerHTML, /data-page-panel="frame"[^>]* hidden/, "frame page must open for a frame-stale restore");
        assert.match(host.innerHTML, /data-page-panel="facts"[^>]* hidden/);
    });

    it("unmount aborts an in-flight generation and drops its result", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 510;
        const current = bootstrap(sourceType, sourceId);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion, "late-v1");
        const pending = pendingSseFetch();
        const { window } = createSandbox((url, options) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                pending.bind(options);
                return pending.promise;
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: current.source,
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        click(host, "assemble");
        await settle();
        instance.unmount();
        assert.strictEqual(host.innerHTML, "");
        pending.resolveFetch(sseResponse("result", {
            source: current.source,
            sourceVersion: current.sourceVersion,
            evidenceSetVersion: current.evidenceSetVersion,
            version
        }));
        await settle();
        await settle();
        assert.strictEqual(host.innerHTML, "", "a late generation must not repaint an unmounted host");
    });
});
