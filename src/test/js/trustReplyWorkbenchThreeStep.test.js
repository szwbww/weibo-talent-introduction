const fs = require("fs");
const path = require("path");
const assert = require("assert");
const vm = require("vm");
const { describe, it } = require("node:test");

const workbenchPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const source = fs.readFileSync(workbenchPath, "utf-8");

// c5 / 15-workbench-three-step（T-6.1 / T-6.2）: 事实集去重行与多问触发（I-5）；
// 步骤 02/03 的高频交互零网络请求（I-4）；重排请求携带条目级 evidenceSetVersion（I-3）
// 与 op* 逐字插槽（I-1 / I-2）。

class FakeElement {
    constructor(ownerDocument) {
        this.ownerDocument = ownerDocument;
        this._innerHTML = "";
        this.listeners = new Map();
        this.dataset = {};
    }
    set innerHTML(value) { this._innerHTML = String(value); }
    get innerHTML() { return this._innerHTML; }
    addEventListener(type, listener) {
        const list = this.listeners.get(type) || [];
        list.push(listener);
        this.listeners.set(type, list);
    }
    removeEventListener(type, listener) {
        this.listeners.set(type, (this.listeners.get(type) || []).filter((item) => item !== listener));
    }
    dispatchEvent(type, event) {
        for (const listener of this.listeners.get(type) || []) listener({ target: event });
    }
    contains() { return true; }
    querySelectorAll() { return []; }
    querySelector() { return null; }
}

class FakeDocument {
    constructor() { this.activeElement = null; }
    createElement() { return new FakeElement(this); }
}

function settle() {
    return new Promise((resolve) => setImmediate(() => setImmediate(resolve)));
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
    return { window, document };
}

function jsonResponse(body) {
    return { ok: true, status: 200, json: async () => body };
}

function click(host, action, dataset) {
    const fields = { action, ...(dataset || {}) };
    host.dispatchEvent("click", {
        dataset: fields,
        closest: () => ({ dataset: fields })
    });
}

function change(host, action, factId, value) {
    host.dispatchEvent("change", {
        dataset: { action, factId },
        value,
        checked: value
    });
}

function input(host, role, requestKey, value) {
    host.dispatchEvent("input", {
        dataset: { role, requestKey },
        value
    });
}

function coverageItem(index, requestKey, factIds, evidenceSetVersion, intentKey, allowedHandlings) {
    return {
        index,
        requestKey,
        requestText: `Question ${index}`,
        status: "GROUNDED",
        factRuleIds: [...factIds],
        intents: [{
            intentKey,
            title: "Intent",
            status: "SUPPORTED",
            evidenceRuleIds: [...factIds],
            missingEvidenceKeys: [],
            requiresResearchContext: false
        }],
        evidenceSetVersion,
        allowedHandlings,
        recommendedHandling: allowedHandlings[0]
    };
}

// 两问来信：R1（index 1）绑定 f7+f9，R3（index 3）绑定 f9+f10 —— f9 被两个来问触发。
function threeStepPayload() {
    const rules = [
        { ruleId: 7, displayName: "Fact Seven", answerBody: "seven body" },
        { ruleId: 9, displayName: "Fact Nine", answerBody: "nine body" },
        { ruleId: 10, displayName: "Fact Ten", answerBody: "ten body" }
    ];
    const coverage = [
        coverageItem(1, "TRAINING_MAIL-601-r1", [7, 9], "per-r1-e1", "enterprise.match", ["ANSWER_WITH_EVIDENCE", "OMIT"]),
        coverageItem(3, "TRAINING_MAIL-601-r3", [9, 10], "per-r3-e1", "application.submit", ["ANSWER_WITH_EVIDENCE", "OMIT"])
    ];
    return {
        source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
        sourceVersion: "TRAINING_MAIL-601-v1",
        inboundSubject: "subject",
        inboundText: "body",
        expertName: "Expert",
        expertEmail: "expert@example.com",
        llmEnabled: true,
        availableModels: ["DEEPSEEK_V4_FLASH"],
        defaultModel: "DEEPSEEK_V4_FLASH",
        suggestedFactIds: [7, 9, 10],
        canonicalFactIds: [7, 9, 10],
        rulesByCategory: rules,
        requestCoverage: coverage,
        requestFactSelections: coverage.map((item) => ({ requestKey: item.requestKey, factRuleIds: [...item.factRuleIds] })),
        draftReadiness: "READY",
        contextWarnings: [],
        evidenceSetVersion: "letter-e1",
        // c5 (I-5): 服务端权威 13 协议——步骤 02/03 的唯一事实来源。
        facts: [
            { id: "f7", topic: "enterprise", body: "seven body", controlled: null, frozen: false, required: true },
            { id: "f9", topic: "application", body: "nine body", controlled: null, frozen: false, required: true },
            { id: "f10", topic: "review", body: "ten body", controlled: null, frozen: false, required: true }
        ],
        paragraphPlan: [
            { topic: "enterprise", factIds: ["f7", "f9"], gapCondition: null },
            { topic: "application", factIds: ["f9"], gapCondition: null },
            { topic: "review", factIds: ["f10"], gapCondition: null }
        ],
        topicOrder: ["enterprise", "application", "review"]
    };
}

function rearrangeResponse(paragraphPlan, facts, paragraphs) {
    return {
        source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
        sourceVersion: "TRAINING_MAIL-601-v1",
        evidenceSetVersion: "letter-e1",
        paragraphPlan,
        facts,
        topicOrder: paragraphPlan.map((entry) => entry.topic),
        paragraphs,
        actionText: null,
        validationCodes: []
    };
}

function factRows(host) {
    return [...host.innerHTML.matchAll(/<tr data-origin="([^"]+)" data-adopted="(true|false)" data-fact-id="([^"]+)"[^>]*>([\s\S]*?)<\/tr>/g)].map((match) => {
        const cells = [...match[4].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/g)].map((cell) => cell[1]);
        return { origin: match[1], adopted: match[2], id: match[3], cells };
    });
}

function paragraphTopics(host) {
    return [...host.innerHTML.matchAll(/data-role="paragraph" data-topic="([^"]+)"/g)].map((match) => match[1]);
}

describe("trust reply workbench three-step (c5)", () => {
    it("renders one deduped row per fact with all triggering question indexes (T-6.1)", async () => {
        const calls = [];
        const { window } = createSandbox((url) => {
            calls.push(String(url));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(threeStepPayload()));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        await settle();

        // 三个页签 + 事实集表格存在（S-1 / S-2）。
        assert.strictEqual((host.innerHTML.match(/role="tab"[^>]*data-page="/g) || []).length, 3);
        assert.match(host.innerHTML, /class="trust-reply-factset"/);

        // I-5 / T-6.1: 行数 == 去重后事实数（f7/f9/f10 各一行，f9 被两个来问触发仍一行）。
        const rows = factRows(host);
        assert.strictEqual(rows.length, 3, "fact set must dedupe to one row per fact");
        const byId = new Map(rows.map((row) => [row.id, row]));

        const f9 = byId.get("f9");
        assert.ok(f9, "f9 row must exist");
        assert.strictEqual(f9.origin, "QA");
        assert.strictEqual(f9.adopted, "true");
        // 触发来问列 = 全部命中 index（R1 · R3）；用量 = 段落引用计数。
        assert.match(f9.cells[2], /R1 · R3/, "trigger column must list every requesting question");
        assert.match(f9.cells[5], /2×/, "usage must count every paragraph reference");

        const f7 = byId.get("f7");
        assert.ok(f7, "f7 row must exist");
        assert.match(f7.cells[2], /R1/, "f7 is triggered by R1 only");
        assert.doesNotMatch(f7.cells[2], /R3/);
        assert.match(f7.cells[5], /1×/);

        const f10 = byId.get("f10");
        assert.ok(f10, "f10 row must exist");
        assert.match(f10.cells[2], /R3/);
        assert.match(f10.cells[5], /1×/);
    });

    it("keeps adopt, topic, pin, edit, merge-up and move interactions at zero network requests (T-6.2 / I-4)", async () => {
        const calls = [];
        const { window } = createSandbox((url) => {
            calls.push(String(url));
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(threeStepPayload()));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        await settle();
        const baseline = calls.length;

        // 取消采用 f9（勾选 → false）：只改本地草稿。
        change(host, "factset-adopt", "f9", false);
        let rows = factRows(host);
        assert.strictEqual(rows.find((row) => row.id === "f9").adopted, "false");
        assert.strictEqual(calls.length, baseline, "un-adopt must not hit the network");

        // 重新采用 f9。
        change(host, "factset-adopt", "f9", true);
        rows = factRows(host);
        assert.strictEqual(rows.find((row) => row.id === "f9").adopted, "true");
        assert.strictEqual(calls.length, baseline, "re-adopt must not hit the network");

        // 改主题：f7 从 enterprise 移到 application（enterprise 空段被移除）。
        change(host, "factset-topic", "f7", "application");
        rows = factRows(host);
        assert.match(rows.find((row) => row.id === "f7").cells[4], /<option[^>]*selected[^>]*>application<\/option>/);
        assert.deepStrictEqual(paragraphTopics(host), ["review", "application"]);
        assert.strictEqual(calls.length, baseline, "topic change must not hit the network");

        // 段落锁定/解锁（第三页）：只改本地草稿。
        click(host, "paragraph-pin", { topic: "application" });
        assert.match(host.innerHTML, /class="trust-reply-item" data-pinned="true" data-role="paragraph" data-topic="application"/);
        assert.strictEqual(calls.length, baseline, "pin must not hit the network");
        click(host, "paragraph-pin", { topic: "application" });
        assert.doesNotMatch(host.innerHTML, /class="trust-reply-item" data-pinned="true" data-role="paragraph" data-topic="application"/);
        assert.strictEqual(calls.length, baseline, "unpin must not hit the network");

        // 段落编辑开关：只改本地草稿。
        click(host, "paragraph-edit", { topic: "application" });
        assert.match(host.innerHTML, /<textarea class="pre" data-role="paragraph-text" data-topic="application"/);
        click(host, "paragraph-edit", { topic: "application" });
        assert.doesNotMatch(host.innerHTML, /<textarea class="pre" data-role="paragraph-text"/);
        assert.strictEqual(calls.length, baseline, "edit toggle must not hit the network");

        // 上下移：只改本地草稿（当前顺序 review → application）。
        click(host, "paragraph-move-up", { topic: "application" });
        assert.deepStrictEqual(paragraphTopics(host), ["application", "review"]);
        click(host, "paragraph-move-down", { topic: "application" });
        assert.deepStrictEqual(paragraphTopics(host), ["review", "application"]);
        assert.strictEqual(calls.length, baseline, "move up/down must not hit the network");

        // 并入上段：application 并入 review（上方段落）。
        click(host, "paragraph-merge-up", { topic: "application" });
        assert.deepStrictEqual(paragraphTopics(host), ["review"]);
        assert.strictEqual(calls.length, baseline, "merge-up must not hit the network");
    });

    it("sends pinned paragraphs with per-request evidence versions and op facts as verbatim slots (I-3 / I-1)", async () => {
        const payload = threeStepPayload();
        // R1 支持「按回答说明生成」→ 产出 op1 运营事实。
        payload.requestCoverage[0].allowedHandlings = ["ANSWER_WITH_EVIDENCE", "ANSWER_FROM_OPERATOR_INPUT", "OMIT"];
        payload.requestCoverage[0].recommendedHandling = "ANSWER_FROM_OPERATOR_INPUT";
        let rearrangeBody = null;
        let rearrangeCalls = 0;
        const { window } = createSandbox((url, options) => {
            if (url.includes("/bootstrap")) return Promise.resolve(jsonResponse(payload));
            if (url.includes("/generations/stream")) {
                let consumed = false;
                return Promise.resolve({
                    ok: true,
                    status: 200,
                    body: {
                        getReader: () => ({
                            async read() {
                                if (consumed) return { done: true, value: undefined };
                                consumed = true;
                                const frame = `event: result\ndata: ${JSON.stringify({
                                    source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
                                    sourceVersion: "TRAINING_MAIL-601-v1",
                                    evidenceSetVersion: "letter-e1",
                                    version: {
                                        versionId: "v-op1",
                                        requestKey: "TRAINING_MAIL-601-r1",
                                        handling: "ANSWER_FROM_OPERATOR_INPUT",
                                        answerText: "operator fixed text",
                                        claims: [],
                                        model: "DEEPSEEK_V4_FLASH",
                                        generationKind: "AI_GENERATED",
                                        evidenceSetVersion: "per-r1-e1",
                                        sourceVersion: "TRAINING_MAIL-601-v1",
                                        operatorInstructionHash: "",
                                        operatorInstruction: "回答说明"
                                    }
                                })}\n\n`;
                                return { done: false, value: new TextEncoder().encode(frame) };
                            },
                            async cancel() {}
                        })
                    }
                });
            }
            if (url.includes("/rearrange")) {
                rearrangeCalls += 1;
                rearrangeBody = JSON.parse(options.body);
                return Promise.resolve(jsonResponse(rearrangeResponse(
                    [
                        { topic: "enterprise", factIds: ["f7", "f9"], gapCondition: null },
                        { topic: "application", factIds: ["f9", "op1"], gapCondition: null },
                        { topic: "review", factIds: ["f10"], gapCondition: null }
                    ],
                    [
                        { id: "f7", topic: "enterprise", body: "seven body", controlled: null, frozen: false, required: true },
                        { id: "f9", topic: "application", body: "nine body", controlled: null, frozen: false, required: true },
                        { id: "op1", topic: "application", body: "operator fixed text", controlled: null, frozen: true, required: true },
                        { id: "f10", topic: "review", body: "ten body", controlled: null, frozen: false, required: true }
                    ],
                    [
                        { topic: "enterprise", factIds: ["f7", "f9"], text: "seven nine" },
                        { topic: "application", factIds: ["f9", "op1"], text: "nine operator fixed text" },
                        { topic: "review", factIds: ["f10"], text: "ten body" }
                    ]
                )));
            }
            if (url.includes("/api/translate")) return Promise.resolve(jsonResponse({ ok: true, translatedText: "译文" }));
            throw new Error(`unexpected request: ${url}`);
        });
        const host = new FakeElement(window.document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 601 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        await settle();

        // 填写回答说明并生成 op1（按回答说明生成）。
        input(host, "instruction", "TRAINING_MAIL-601-r1", "回答说明");
        click(host, "adjust-item", { requestKey: "TRAINING_MAIL-601-r1" });
        await settle();
        await settle();

        const opRow = factRows(host).find((row) => row.id === "op1");
        assert.ok(opRow, "op1 must appear in the fact set after 按回答说明生成");
        assert.strictEqual(opRow.origin, "OPERATOR");
        assert.match(opRow.cells[3], /运营 · 逐字/);

        // 锁定 application 段落后重排：只触发一次 /rearrange。
        click(host, "paragraph-pin", { topic: "application" });
        assert.match(host.innerHTML, /class="trust-reply-item" data-pinned="true" data-role="paragraph" data-topic="application"/);
        click(host, "rearrange");
        await settle();
        await settle();

        assert.strictEqual(rearrangeCalls, 1, "rearrange must hit the server exactly once");
        assert.ok(rearrangeBody, "rearrange must carry a request body");
        // I-3: pinned 段落携带条目级 evidenceSetVersion（主属 R1），非全信标量。
        assert.ok(
            !rearrangeBody.pinnedParagraphs.some((p) => p.evidenceSetVersion === "letter-e1"),
            "pinned must never carry the whole-letter scalar (I-3)"
        );
        const pinnedApplication = rearrangeBody.pinnedParagraphs.find((p) => p.topic === "application");
        assert.ok(pinnedApplication, "application paragraph must be submitted as pinned");
        assert.strictEqual(pinnedApplication.evidenceSetVersion, "per-r1-e1");
        // I-1/I-2: op1 以逐字插槽（body 原样、frozen）提交；id 为 op<n> 绝非哈希。
        const op1 = rearrangeBody.operatorFacts.find((fact) => fact.id === "op1");
        assert.ok(op1, "op1 must be submitted in operatorFacts");
        assert.strictEqual(op1.body, "operator fixed text");
        assert.ok(rearrangeBody.operatorFacts.every((fact) => /^op\d+$/.test(fact.id)), "op ids must use the op<n> space, never hashes (I-1)");
    });
});
