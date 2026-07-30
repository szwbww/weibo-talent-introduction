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
    }

    set innerHTML(value) {
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
        await settle();

        assert.strictEqual(calls[1].options.headers.Accept, "text/event-stream, application/json");
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

    it("automatically generates and adopts grounded items once per mount", async () => {
        const current = bootstrap("TRAINING_MAIL", 306);
        const requestKey = current.requestCoverage[0].requestKey;
        const version = itemVersion(requestKey, current.sourceVersion, current.evidenceSetVersion);
        const calls = [];
        const { window } = createSandbox((url, options) => {
            calls.push({ url, options });
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/generations/stream")) {
                return Promise.resolve(sseResponse("result", {
                    source: current.source,
                    sourceVersion: current.sourceVersion,
                    evidenceSetVersion: current.evidenceSetVersion,
                    itemVersions: [version]
                }));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "回答译文" }));
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

        const fullDrafts = calls
            .map((call) => JSON.parse(call.options.body))
            .filter((payload) => payload.operation === "FULL_DRAFT");
        assert.strictEqual(fullDrafts.length, 1);
        assert.match(host.innerHTML, /data-locked="true"/);
        assert.match(host.innerHTML, /已处理 1\/1/);
        assert.match(host.innerHTML, /data-role="item-body" hidden/);
        assert.doesNotMatch(host.innerHTML, /data-action="generate-all"/);
        click(host, "toggle-item", requestKey);
        click(host, "translate-answer", requestKey, "v1");
        await settle();
        assert.match(host.innerHTML, /回答译文/);
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
        const unsupported = {
            ...current.requestCoverage[0],
            index: 1,
            requestKey: "LIVE_INBOUND-310-request-unsupported",
            requestText: "Unsupported question",
            status: "UNSUPPORTED",
            factRuleIds: [],
            allowedHandlings: ["ANSWER_FROM_OPERATOR_INPUT", "OMIT"],
            recommendedHandling: "ANSWER_FROM_OPERATOR_INPUT"
        };
        current.requestCoverage.push(unsupported);
        const fullGeneration = deferred();
        const { window } = createSandbox((url) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(current));
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "问题译文" }));
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
        host.dispatchEvent("input", {
            dataset: { role: "instruction", requestKey: unsupported.requestKey },
            value: "请说明企业案例需以官网为准"
        });

        const actionButton = host.innerHTML.match(new RegExp(`<button[^>]*data-action="adjust-item"[^>]*data-request-key="${unsupported.requestKey}"[^>]*>[^<]*<\\/button>`))?.[0];
        assert.ok(actionButton, "unsupported item must render an AI generation action");
        assert.doesNotMatch(actionButton, /\sdisabled/);
        click(host, "adjust-item", unsupported.requestKey);
        await settle();

        assert.match(host.innerHTML, /正在生成其他回复，请稍后/);
        fullGeneration.resolve(sseResponse("result", {
            source: current.source,
            sourceVersion: current.sourceVersion,
            evidenceSetVersion: current.evidenceSetVersion,
            itemVersions: [itemVersion(current.requestCoverage[0].requestKey, current.sourceVersion, current.evidenceSetVersion)]
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
        assert.match(host.innerHTML, /AI generation failed/);
        host.dispatchEvent("change", { dataset: { role: "handling", requestKey }, value: "OMIT" });
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
        await settle();
        assert.doesNotMatch(host.innerHTML, /版本 1|value="v1"/);
        assert.match(host.innerHTML, /data-action="complete" disabled/);
        assert.match(host.innerHTML, /来源或事实已变化/);
    });

    it("auto adopts grounded versions and invalidates assembly when a decision changes", async () => {
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
});
