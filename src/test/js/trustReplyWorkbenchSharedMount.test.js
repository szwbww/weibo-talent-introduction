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

function click(host, action, requestKey, versionId) {
    host.dispatchEvent("click", {
        dataset: { action, requestKey, versionId },
        closest: () => ({ dataset: { action, requestKey, versionId } })
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
        allowedHandlings: ["ANSWER_WITH_EVIDENCE", "OMIT"],
        recommendedHandling: "ANSWER_WITH_EVIDENCE"
    }],
    draftReadiness: "READY",
    contextWarnings: [],
    evidenceSetVersion: `${sourceType}-${sourceId}-e1`
});

describe("shared trust reply workbench mount contract", () => {
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

    it("keeps the shared card and fact-option style contract", () => {
        const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
        const styles = fs.readFileSync(stylesPath, "utf-8");
        assert.match(source, /class="compose-panel trust-reply-item"/);
        assert.match(styles, /\.trust-reply-fact-option\s*\{/);
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
        okMount.host.dispatchEvent("change", { dataset: { role: "fact" }, value: "2", checked: true, matches: () => true });
        await settle();
        assert.strictEqual(okMount.statePayloads.length, 1);
        assert.deepStrictEqual(okMount.statePayloads[0].lockedItems, []);
        assert.strictEqual(okMount.statePayloads[0].expectedStateVersion, 4);
        assert.ok(okMount.getBootstrapCalls() >= 2, "facts must re-bootstrap after a successful delete");

        const failMount = buildMount(true);
        await settle();
        await settle();
        failMount.host.dispatchEvent("change", { dataset: { role: "fact" }, value: "2", checked: true, matches: () => true });
        await settle();
        assert.strictEqual(failMount.statePayloads.length, 1);
        assert.strictEqual(failMount.getBootstrapCalls(), 1, "delete failure must not switch facts");
        assert.match(failMount.host.innerHTML, /旧锁定状态删除失败/);
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
});
