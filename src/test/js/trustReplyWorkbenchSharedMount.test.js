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
        this.innerHTML = "";
        this.hidden = false;
        this.listeners = new Map();
        this.attributes = new Map();
        this.dataset = {};
    }

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

function click(host, action, requestKey) {
    host.dispatchEvent("click", {
        dataset: { action, requestKey },
        closest: () => ({ dataset: { action, requestKey } })
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
        click(host, "generate-all");
        await settle();

        const payload = JSON.parse(calls[1].options.body);
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
        click(host, "generate-all");
        await settle();

        assert.strictEqual(calls[1].options.headers.Accept, "text/event-stream, application/json");
        assert.match(host.innerHTML, /TRUST_REPLY_GENERATION_ID_INVALID/);
    });

    it("writes global model and timeout controls into the generation payload", async () => {
        const current = bootstrap("TRAINING_MAIL", 304);
        current.availableModels = ["DEEPSEEK_V4_FLASH", "DEEPSEEK_V4_PRO"];
        const calls = [];
        const responses = [
            jsonResponse(current),
            { ok: false, status: 400, json: async () => ({ code: "EXPECTED_TEST_STOP" }) }
        ];
        const { window } = createSandbox((url, options) => {
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
        click(host, "generate-all");
        await settle();

        const payload = JSON.parse(calls[1].options.body);
        assert.strictEqual(payload.model, "DEEPSEEK_V4_PRO");
        assert.strictEqual(payload.llmAttemptTimeoutSeconds, 60);
        assert.strictEqual(payload.llmTotalTimeoutSeconds, 600);
    });

    it("rejects a foreign item result before it can become a version or lock", async () => {
        const sourceType = "TRAINING_MAIL";
        const sourceId = 303;
        const current = bootstrap(sourceType, sourceId);
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
        click(host, "generate-all");
        await settle();
        assert.doesNotMatch(host.innerHTML, /版本 1|value="v1"/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
        assert.match(host.innerHTML, /来源或事实已变化/);
    });

    it("invalidates an old assembly on missing full identity or stale terminal error", async () => {
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
                itemVersions: [version]
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
            }),
            sseResponse("error", { code: "TRUST_REPLY_SOURCE_STALE", message: "stale source" })
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
        click(host, "generate-all");
        await settle();
        click(host, "lock-item", requestKey);
        await settle();
        click(host, "assemble");
        await settle();
        assert.match(host.innerHTML, /server draft/);
        click(host, "generate-all");
        await settle();
        assert.doesNotMatch(host.innerHTML, /server draft/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
        assert.match(host.innerHTML, /来源或事实已变化|stale source/);
    });

    it("keeps the shared card and fact-option style contract", () => {
        const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
        const styles = fs.readFileSync(stylesPath, "utf-8");
        assert.match(source, /class="compose-panel trust-reply-item"/);
        assert.match(styles, /\.trust-reply-fact-option\s*\{/);
    });
});
