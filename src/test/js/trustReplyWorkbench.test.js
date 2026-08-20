const fs = require("fs");
const path = require("path");
const assert = require("assert");
const vm = require("vm");
const { describe, it } = require("node:test");

const appPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const workbenchPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const app = fs.readFileSync(appPath, "utf-8");
const workbench = fs.readFileSync(workbenchPath, "utf-8");
const styles = fs.readFileSync(stylesPath, "utf-8");

describe("shared trust reply workbench", () => {
    it("keeps the component as the only workbench implementation", () => {
        assert.match(workbench, /function mount\(host, options\)/);
        assert.match(workbench, /function requestJson\(/);
        assert.match(workbench, /function requestSse\(/);
        assert.match(workbench, /function resetVersions\(/);
        assert.match(workbench, /function toggleResolve\(/);
        assert.match(workbench, /function assemble\(/);
        assert.match(workbench, /lockedItems/);
        assert.match(workbench, /rawDraftText/);
        assert.match(workbench, /generationKind/);
        assert.doesNotMatch(app, /composedReplyState|renderComposedReplyWorkbenchHtml|trust-generate-draft/);
    });

    it("renders two tab panels with shared state and fixed completion labels", () => {
        assert.match(workbench, /role="tablist"/);
        assert.match(workbench, /data-action="set-page"/);
        assert.match(workbench, /data-page-panel="facts"/);
        assert.match(workbench, /data-page-panel="frame"/);
        assert.match(workbench, /function setActivePage\(/);
        assert.match(workbench, /aria-selected/);
        assert.match(workbench, /data-role="handling"/);
        assert.match(workbench, /data-role="instruction"/);
        assert.match(workbench, /data-role="version"/);
        assert.match(workbench, /resolve-item/);
        assert.match(workbench, /data-action="toggle-item"/);
        assert.match(workbench, /ANSWER_FROM_OPERATOR_INPUT/);
        assert.match(workbench, /function resolvedVersion\(/);
        assert.match(workbench, /function requestTranslation\(/);
        assert.doesNotMatch(workbench, /data-action="generate-all"/);
        assert.match(workbench, /data-action="cancel-generation"/);
        assert.match(workbench, /模拟 · 不外发/);
        assert.match(workbench, /正式回复/);
        assert.match(workbench, /完成模拟并评估/);
        assert.match(workbench, /采用到人工回复/);
        assert.doesNotMatch(workbench, /mode-switch|mode-selector|modeToggle/i);
    });

    it("uses server identity and does not locally compose answer text", () => {
        assert.match(workbench, /sourceId: Number\(options\.source\.sourceId\)/);
        assert.match(workbench, /expectedSourceVersion: state\.sourceVersion/);
        assert.match(workbench, /expectedEvidenceSetVersion: state\.evidenceSetVersion/);
        assert.match(workbench, /rawDraftText \|\|/);
        assert.doesNotMatch(workbench, /answers\.join|dedupe|truncate|LLM rewrite/i);
    });

    it("sends the canonical matrix and frame snapshot instead of flat facts", () => {
        assert.match(workbench, /function serializeRequestFactSelections\(/);
        assert.match(workbench, /requestFactSelections: serializeRequestFactSelections\(\)/);
        assert.match(workbench, /frameSnapshot: state\.frameSnapshot/);
        assert.match(workbench, /function sameFrameSnapshot\(/);
        assert.match(workbench, /function factOwnerById\(/);
        assert.doesNotMatch(workbench, /requestedFactIds/);
        assert.doesNotMatch(workbench, /selectedFactIds/);
        assert.doesNotMatch(workbench, /\[data-role="fact"\]/);
        assert.match(workbench, /trust-reply-workbench-state-v3/);
    });

    it("keeps explicit per-item generation triggers and no mount-time full draft", () => {
        assert.doesNotMatch(workbench, /initialFullDraftSourceVersions/);
        assert.doesNotMatch(workbench, /void generateAll\(\)/);
        assert.match(workbench, /function generateMissingGrounded\(/);
        assert.match(workbench, /function computeReadiness\(/);
        assert.match(workbench, /data-action="assemble"/);
        assert.match(workbench, /operation: "ADJUST_ITEM"/);
        assert.doesNotMatch(workbench, /"FULL_DRAFT"/);
        assert.doesNotMatch(workbench, /data-action="generate-all"/);
    });

    it("only accepts a server assembly for completion and shows preview states", () => {
        assert.match(workbench, /function assemblyIdentityMatches\(/);
        assert.match(workbench, /function previewState\(/);
        assert.match(workbench, /previewState\(\) !== "CURRENT"/);
        assert.match(workbench, /配置预览 · 尚未服务端整合/);
        assert.match(workbench, /服务端整合完成/);
        assert.match(workbench, /配置已变化 · 请重新整合/);
        assert.match(workbench, /data-role="local-preview"/);
        assert.match(workbench, /data-role="raw-preview"/);
    });

    it("exposes per-card fact chips and picker with owner labels", () => {
        assert.match(workbench, /data-action="add-fact"/);
        assert.match(workbench, /data-action="remove-fact"/);
        assert.match(workbench, /data-action="toggle-fact-picker"/);
        assert.match(workbench, /已用于摘要 /);
        assert.match(workbench, /已选择/);
        assert.match(workbench, /保存中/);
        assert.match(workbench, /class="trust-reply-fact-picker-option"/);
    });

    it("exposes the one-click orchestration, machine-fill badge and separate verdict lines", () => {
        assert.match(workbench, /data-action="auto-run"/);
        assert.match(workbench, /data-action="auto-reset"/);
        assert.match(workbench, /一键预判/);
        assert.match(workbench, /class="trust-reply-autofilled">机器代填/);
        assert.match(workbench, /function autoRun\(/);
        assert.match(workbench, /function autoReset\(/);
        assert.match(workbench, /function runItemSequence\(/);
        assert.match(workbench, /function renderVerdict\(/);
        // I-5/R-2: assembly completion and send clearance are separate strings;
        // the decision comes only from the retained preview evidence, never
        // from a non-empty assembly.
        assert.match(workbench, /汇总已完成/);
        assert.match(workbench, /硬性闸门：尚未预判/);
        assert.match(workbench, /尚未预判/);
        assert.match(workbench, /判定：\$\{escapeText\(decision\)\}/);
        assert.match(workbench, /"可自动发" : "转人工"/);
        assert.match(workbench, /wouldBeBlockedBy/);
        assert.doesNotMatch(workbench, /汇总完成.*可自动发送/s);
        assert.doesNotMatch(workbench, /可自动发送/);
        assert.doesNotMatch(workbench, /state\.assembly[^;]*可自动发|可自动发[^;]*state\.assembly/);
        // T4-3: the read-only host never renders the aggregate bar.
        assert.match(workbench, /state\.readOnly \? "" : `<div class="trust-reply-autorun"/);
    });

    it("keeps page code as thin training/live adapters", () => {
        assert.match(app, /mountAiTrainingTrustReply/);
        assert.match(app, /source: \{ sourceType: "TRAINING_MAIL", sourceId: Number\(mail\.mailRecordId\) \}/);
        assert.match(app, /mountLiveTrustReply/);
        assert.match(app, /source: \{ sourceType: "LIVE_INBOUND", sourceId: Number\(recordId\) \}/);
        assert.match(app, /rawTemplate: assembly\.rawDraftText/);
        assert.match(app, /function buildTrustReplyAssemblySnapshot\(/);
        assert.doesNotMatch(app, /aiTrainingSimulateBtn|aiTrainingSimulateMessages|aiTrainingReplyModel/);
    });
});

// ---- P3: fact chip horizontal drag reorder + keyboard equivalent ----

class FakeElement {
    constructor(ownerDocument) {
        this.ownerDocument = ownerDocument;
        this._innerHTML = "";
        this.listeners = new Map();
        this.dataset = {};
    }
    set innerHTML(value) {
        this._innerHTML = String(value);
    }
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
        for (const listener of this.listeners.get(type) || []) listener(event);
    }
    contains() { return true; }
    querySelector(selector) {
        const match = selector.match(/^\[data-fact-id="(\d+)"\] \[data-role="fact-grip"\]$/);
        if (match && this._innerHTML.includes(`data-fact-id="${match[1]}"`)) {
            const grip = new FakeElement(this.ownerDocument);
            grip.focus = () => { this.ownerDocument.lastFocusedFactId = match[1]; };
            return grip;
        }
        return null;
    }
}

class FakeDocument {
    constructor() {
        this.activeElement = null;
        this.lastFocusedFactId = null;
    }
    createElement() { return new FakeElement(this); }
}

function settle() {
    return new Promise((resolve) => setImmediate(() => setImmediate(resolve)));
}

function createSandbox(fetchImpl, confirmImpl) {
    const document = new FakeDocument();
    const window = {
        document,
        fetch: fetchImpl,
        confirm: confirmImpl || (() => true),
        crypto: { randomUUID: () => "00000000-0000-4000-8000-000000000001" },
        AbortController,
        TextDecoder,
        TextEncoder,
        setTimeout,
        clearTimeout
    };
    const sandbox = { window, document, console, setTimeout, clearTimeout, AbortController, TextDecoder, TextEncoder };
    vm.createContext(sandbox);
    vm.runInContext(workbench, sandbox);
    return { sandbox, window, document };
}

function bootstrapPayload(sourceType, sourceId, factIds, droppedFactIds) {
    const droppedIds = droppedFactIds || [];
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
        suggestedFactIds: [...factIds],
        canonicalFactIds: [...factIds],
        rulesByCategory: [...factIds, ...droppedIds].map((id) => ({ ruleId: id, displayName: `Fact ${id}`, answerBody: `body ${id}` })),
        requestCoverage: [{
            index: 0,
            requestKey: `${sourceType}-${sourceId}-request`,
            requestText: "Question",
            status: "GROUNDED",
            factRuleIds: [...factIds],
            // P1 (I-2): server-side shadow field, carried for the muted hint only.
            droppedFactRuleIds: [...droppedIds],
            allowedHandlings: ["ANSWER_WITH_EVIDENCE", "OMIT"],
            recommendedHandling: "ANSWER_WITH_EVIDENCE"
        }],
        draftReadiness: "READY",
        contextWarnings: [],
        evidenceSetVersion: `${sourceType}-${sourceId}-e1`
    };
}

function renderedFactIds(host) {
    return [...host.innerHTML.matchAll(/class="trust-reply-fact-chip" data-fact-id="(\d+)"/g)].map((match) => match[1]);
}

function chipEl(factId, requestKey, list) {
    const chip = {
        dataset: { factId: String(factId), requestKey },
        getBoundingClientRect: () => ({ left: 0, width: 100 }),
        closest(selector) {
            if (selector === ".trust-reply-fact-chip") return chip;
            if (selector === '[data-role="fact-chip-list"]') return list;
            return null;
        }
    };
    return chip;
}

function gripEl(chip) {
    const grip = {
        dataset: { role: "fact-grip" },
        getAttribute(name) {
            if (name === "aria-disabled") return chip.dataset.factGripDisabled ? "true" : null;
            return null;
        },
        closest(selector) {
            if (selector === '[data-role="fact-grip"]') return grip;
            if (selector === ".trust-reply-fact-chip") return chip;
            return null;
        }
    };
    return grip;
}

function listEl(chips) {
    return {
        dataset: {},
        querySelectorAll(selector) {
            return selector === ".trust-reply-fact-chip" ? chips : [];
        }
    };
}

function makeDataTransfer() {
    return {
        data: null,
        effectAllowed: "",
        setData(type, value) { this.data = value; },
        getData() { return this.data; }
    };
}

function event(target, extra) {
    return { target, preventDefault() {}, stopPropagation() {}, ...extra };
}

describe("fact order drag (P3)", () => {
    it("renders the S-1 grip skeleton and S-3 hint with no inline styles", () => {
        assert.match(workbench, /data-role="fact-grip"/);
        assert.match(workbench, /trust-reply-fact-grip/);
        assert.match(workbench, /draggable="true"/);
        assert.match(workbench, /data-role="fact-grip"[^>]*tabindex="0"/);
        assert.match(workbench, /aria-label="拖动或用左右方向键调整/);
        assert.match(workbench, /aria-describedby=/);
        assert.match(workbench, /trust-reply-fact-grip-hint/);
        assert.match(workbench, /拖动 ⋮⋮ 或用 ← → 调整顺序/);
        // K-dom-stub-tests-hide-dangling-refs: real styles.css must carry the
        // new classes (the DOM stub would render fine without them).
        assert.match(styles, /\.trust-reply-fact-grip \{/);
        assert.match(styles, /data-drop-before="true"\]/);
        assert.match(styles, /data-drop-after="true"\]/);
        assert.match(styles, /trust-reply-fact-grip-hint/);
        // S-1 禁止项: no new inline styles.
        assert.strictEqual((workbench.match(/style=/g) || []).length, 1);
        // I-2: no sort/reverse on factRuleIds anywhere.
        assert.doesNotMatch(workbench, /factRuleIds.*(sort|reverse)/);
    });

    it("exposes the pure reorderFactIds contract (move, front, end, missing, clamp, length)", () => {
        const { window } = createSandbox(() => Promise.resolve({ ok: true, status: 200, json: async () => ({}) }));
        const reorder = window.TrustReplyWorkbench.reorderFactIds;
        // Sandbox arrays live in a different V8 realm, so compare by JSON.
        const reordered = (ids, fromId, toIndex) => JSON.stringify(reorder(ids, fromId, toIndex));
        assert.strictEqual(reordered([1, 2, 3], 2, 0), "[2,1,3]"); // 前移
        assert.strictEqual(reordered([1, 2, 3], 2, 2), "[1,3,2]"); // 后移
        assert.strictEqual(reordered([1, 2, 3], 3, 0), "[3,1,2]"); // 移到首位
        assert.strictEqual(reordered([1, 2, 3], 1, 2), "[2,3,1]"); // 移到末位
        assert.strictEqual(reordered([1, 2, 3], 99, 0), "[1,2,3]"); // 不存在的 id 原样返回
        assert.strictEqual(reordered([1, 2, 3], 1, 99), "[2,3,1]"); // 越界 toIndex 钳到末位
        assert.strictEqual(reordered([1, 2, 3], 1, -5), "[1,2,3]"); // 越界 toIndex 钳到首位
        assert.strictEqual(reorder([1, 2, 3], 2, 1).length, 3); // 长度守恒
        assert.strictEqual(reordered([], 1, 0), "[]"); // 空输入
    });

    it("keyboard ArrowLeft/ArrowRight reorders via changeRequestFacts and restores grip focus", async () => {
        let currentFactIds = [1, 2, 3];
        const calls = [];
        const { window, document } = createSandbox((url, options) => {
            const body = JSON.parse(options.body);
            calls.push({ url, body });
            if (Array.isArray(body.requestFactSelections) && body.requestFactSelections.length) {
                currentFactIds = [...(body.requestFactSelections[0].factRuleIds || [])];
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, currentFactIds) });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        const requestKey = "TRAINING_MAIL-101-request";
        const list = listEl([]);
        const chips = [1, 2, 3].map((id) => chipEl(id, requestKey, list));
        list.querySelectorAll = () => chips;

        // ArrowLeft on the second chip moves it to the front.
        host.dispatchEvent("keydown", event(gripEl(chips[1]), { key: "ArrowLeft" }));
        await settle();
        assert.deepStrictEqual(renderedFactIds(host), ["2", "1", "3"]);
        const payload = calls[calls.length - 1].body.requestFactSelections[0];
        assert.deepStrictEqual(payload.factRuleIds, [2, 1, 3]); // I-2: payload = rendered order
        assert.strictEqual(document.lastFocusedFactId, "2"); // I-3: focus restored to the same fact

        // ArrowRight on the chip now at the head (chip 2) moves it back one slot.
        host.dispatchEvent("keydown", event(gripEl(chips[1]), { key: "ArrowRight" }));
        await settle();
        assert.deepStrictEqual(renderedFactIds(host), ["1", "2", "3"]);
    });

    it("dragstart/dragover/drop reorders through the same path and clears drop marks", async () => {
        let currentFactIds = [1, 2, 3];
        const calls = [];
        const { window, document } = createSandbox((url, options) => {
            const body = JSON.parse(options.body);
            calls.push({ url, body });
            if (Array.isArray(body.requestFactSelections) && body.requestFactSelections.length) {
                currentFactIds = [...(body.requestFactSelections[0].factRuleIds || [])];
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, currentFactIds) });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        const requestKey = "TRAINING_MAIL-101-request";
        const list = listEl([]);
        const chips = [1, 2, 3].map((id) => chipEl(id, requestKey, list));
        list.querySelectorAll = () => chips;
        const dt = makeDataTransfer();

        // Drag chip 1 onto the right half of chip 3 -> drop after it.
        host.dispatchEvent("dragstart", event(gripEl(chips[0]), { dataTransfer: dt }));
        assert.strictEqual(chips[0].dataset.dragging, "true");
        assert.strictEqual(dt.data, "1");
        host.dispatchEvent("dragover", event(chips[2], { clientX: 160, dataTransfer: dt }));
        assert.strictEqual(chips[2].dataset.dropAfter, "true");
        host.dispatchEvent("drop", event(chips[2], { clientX: 160, dataTransfer: dt }));
        await settle();

        assert.deepStrictEqual(renderedFactIds(host), ["2", "3", "1"]);
        assert.strictEqual(calls[calls.length - 1].body.requestFactSelections[0].factRuleIds.join(","), "2,3,1");

        // Drag chip 1 onto the left half of chip 2 -> drop before it.
        host.dispatchEvent("dragstart", event(gripEl(chips[0]), { dataTransfer: dt }));
        host.dispatchEvent("dragover", event(chips[1], { clientX: 20, dataTransfer: dt }));
        assert.strictEqual(chips[1].dataset.dropBefore, "true");
        host.dispatchEvent("drop", event(chips[1], { clientX: 20, dataTransfer: dt }));
        await settle();
        assert.deepStrictEqual(renderedFactIds(host), ["1", "2", "3"]);
        assert.strictEqual(calls[calls.length - 1].body.requestFactSelections[0].factRuleIds.join(","), "1,2,3");
        assert.strictEqual(chips[0].dataset.dragging, undefined); // dragend cleanup path
        assert.strictEqual(chips[2].dataset.dropAfter, undefined);
    });

    it("a no-op reorder never calls changeRequestFacts (no confirm, no fetch)", async () => {
        let currentFactIds = [1, 2, 3];
        const calls = [];
        let confirmCalls = 0;
        const { window, document } = createSandbox((url, options) => {
            const body = JSON.parse(options.body);
            calls.push({ url, body });
            if (Array.isArray(body.requestFactSelections) && body.requestFactSelections.length) {
                currentFactIds = [...(body.requestFactSelections[0].factRuleIds || [])];
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, currentFactIds) });
        }, () => { confirmCalls += 1; return true; });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        const requestKey = "TRAINING_MAIL-101-request";
        const list = listEl([]);
        const chips = [1, 2, 3].map((id) => chipEl(id, requestKey, list));
        list.querySelectorAll = () => chips;
        const fetchCountBefore = calls.length;

        // First chip + ArrowLeft: already at the head -> must short-circuit.
        host.dispatchEvent("keydown", event(gripEl(chips[0]), { key: "ArrowLeft" }));
        await settle();
        // Drop a chip onto itself: same position -> must short-circuit.
        const dt = makeDataTransfer();
        host.dispatchEvent("dragstart", event(gripEl(chips[1]), { dataTransfer: dt }));
        host.dispatchEvent("dragover", event(chips[1], { clientX: 160, dataTransfer: dt }));
        host.dispatchEvent("drop", event(chips[1], { clientX: 160, dataTransfer: dt }));
        await settle();

        assert.strictEqual(calls.length, fetchCountBefore); // changeRequestFacts 零调用 -> 无 bootstrap
        assert.strictEqual(confirmCalls, 0);
        assert.deepStrictEqual(renderedFactIds(host), ["1", "2", "3"]);
    });

    it("disabled state ignores dragstart and ArrowLeft (factRuleIds unchanged)", async () => {
        let currentFactIds = [1, 2, 3];
        const calls = [];
        let confirmCalls = 0;
        const { window, document } = createSandbox((url, options) => {
            const body = JSON.parse(options.body);
            calls.push({ url, body });
            if (Array.isArray(body.requestFactSelections) && body.requestFactSelections.length) {
                currentFactIds = [...(body.requestFactSelections[0].factRuleIds || [])];
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, currentFactIds) });
        }, () => { confirmCalls += 1; return true; });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        const requestKey = "TRAINING_MAIL-101-request";
        const list = listEl([]);
        const chips = [1, 2, 3].map((id) => chipEl(id, requestKey, list));
        list.querySelectorAll = () => chips;
        chips.forEach((chip) => { chip.dataset.factGripDisabled = true; });
        const fetchCountBefore = calls.length;
        const dt = makeDataTransfer();

        host.dispatchEvent("keydown", event(gripEl(chips[1]), { key: "ArrowLeft" }));
        host.dispatchEvent("dragstart", event(gripEl(chips[0]), { dataTransfer: dt }));
        host.dispatchEvent("dragover", event(chips[2], { clientX: 160, dataTransfer: dt }));
        host.dispatchEvent("drop", event(chips[2], { clientX: 160, dataTransfer: dt }));
        await settle();

        assert.strictEqual(calls.length, fetchCountBefore);
        assert.strictEqual(confirmCalls, 0);
        assert.deepStrictEqual(renderedFactIds(host), ["1", "2", "3"]);
        assert.strictEqual(chips[0].dataset.dragging, undefined);
    });

    it("moveFact only commits through changeRequestFacts (I-1, no second direct path)", () => {
        const moveBody = workbench.slice(workbench.indexOf("async function moveFact"), workbench.indexOf("function onGripArrowKey"));
        assert.match(moveBody, /changeRequestFacts/);
        assert.doesNotMatch(moveBody, /serializeRequestFactSelections|requestJson|fetch\(/);
    });
});

// ---- P0: SSE error-code rendering + bootstrap-failure reset entry ----

function actionButton(action, requestKey) {
    return {
        dataset: requestKey ? { action, requestKey } : { action },
        closest(selector) {
            if (selector === "[data-action]") return this;
            return null;
        }
    };
}

function sseStream(sseText) {
    const encoder = new TextEncoder();
    return new ReadableStream({
        start(controller) {
            controller.enqueue(encoder.encode(sseText));
            controller.close();
        }
    });
}

describe("P0 SSE error code and state reset", () => {
    it("error event code renders the mapped chinese text", async () => {
        const { window, document } = createSandbox((url, options) => {
            if (String(url).endsWith("/generations/stream")) {
                return Promise.resolve({
                    ok: true,
                    status: 200,
                    body: sseStream('event: error\ndata: {"code":"TRUST_REPLY_ITEM_GENERATION_FAILED","message":"AI generation failed"}\n\n')
                });
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, [1, 2, 3]) });
        }, () => false);
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        host.dispatchEvent("click", event(actionButton("adjust-item", "TRAINING_MAIL-101-request")));
        await settle();

        assert.ok(host.innerHTML.includes("AI 未能产出可用的回答，请重试或换一种处理方式。"));
        assert.ok(!host.innerHTML.includes("AI generation failed"));
    });

    it("bootstrap failure shell offers the reset button", async () => {
        const { window, document } = createSandbox(() => Promise.resolve({
            ok: false,
            status: 422,
            json: async () => ({ code: "TRUST_REPLY_FACT_SELECTION_INVALID" })
        }));
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        assert.ok(host.innerHTML.includes('data-action="reset-workbench-state"'));
        assert.ok(!/style=/.test(host.innerHTML));
    });

    it("successful bootstrap never renders the reset button", async () => {
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, [1, 2, 3]) });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        assert.ok(!host.innerHTML.includes('data-action="reset-workbench-state"'));
        assert.ok(host.innerHTML.includes("可信回复工作台"));
    });
});

// ---- P1: dropped-binding muted hint + never-sent-back shadow field ----

describe("P1 dropped binding hints", () => {
    it("dropped bindings render a muted hint under the fact section", async () => {
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, [], [10, 20]) });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        const droppedSpan = /<span class="muted" data-role="item-facts-dropped">([^<]*)<\/span>/.exec(host.innerHTML);
        assert.ok(droppedSpan, "dropped hint must render under the fact section");
        assert.ok(droppedSpan[1].includes("Fact 10"), "hint lists the first dropped display name");
        assert.ok(droppedSpan[1].includes("Fact 20"), "hint lists the second dropped display name");
        // S-1 禁止项: 提示片段无 inline style，且未引入新 CSS class。
        assert.ok(!/style=/.test(droppedSpan[0]));
        assert.ok(!host.innerHTML.includes("trust-reply-fact-dropped"));
    });

    it("no dropped bindings renders no hint", async () => {
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, [1, 2]) });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        assert.ok(!host.innerHTML.includes('data-role="item-facts-dropped"'));
    });

    it("dropped bindings are never sent back to the server", async () => {
        let generationBody = null;
        const { window, document } = createSandbox((url, options) => {
            const body = JSON.parse(options.body);
            if (String(url).endsWith("/generations/stream")) {
                generationBody = body;
                return Promise.resolve({
                    ok: true,
                    status: 200,
                    body: sseStream('event: error\ndata: {"code":"TRUST_REPLY_ITEM_GENERATION_FAILED","message":"AI generation failed"}\n\n')
                });
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, [], [10, 20]) });
        }, () => true);
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        host.dispatchEvent("click", event(actionButton("adjust-item", "TRAINING_MAIL-101-request")));
        await settle();

        assert.ok(generationBody, "a generation request must have been sent");
        assert.ok(Array.isArray(generationBody.requestFactSelections));
        generationBody.requestFactSelections.forEach((selection) => {
            // I-5/B-2: the canonical matrix carries ONLY requestKey + factRuleIds.
            assert.deepStrictEqual(Object.keys(selection).sort(), ["factRuleIds", "requestKey"]);
        });
    });
});
