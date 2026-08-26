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
        assert.match(workbench, /intentMismatchFactRuleIds/);
        assert.doesNotMatch(workbench, /droppedFactRuleIds|droppedFactLabels/);
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

    it("renders the three-tab preview with rendered default and no querySelector (计划 03)", () => {
        // I-6: default preview reads the rendered draft with the raw fallback.
        assert.match(workbench, /renderedDraftText \|\| assembly\.rawDraftText/);
        // S-2: the shared pre renders exactly one literal data-role per tab;
        // the rendered DOM form data-role="rendered-preview" is asserted in the
        // sandbox cases of autoRunOrchestration.test.js.
        assert.match(workbench, /<pre class="pre" data-role="rendered-preview">/);
        assert.match(workbench, /<pre class="pre" data-role="local-preview">/);
        assert.match(workbench, /<pre class="pre" data-role="raw-preview">/);
        // S-1: the preview tab bar uses the contract action + key shape; the
        // three keys/labels are source literals (the rendered literal form
        // data-action="set-preview-tab" data-preview-tab="rendered" is asserted
        // in the sandbox cases of autoRunOrchestration.test.js).
        assert.match(workbench, /data-action="set-preview-tab" data-preview-tab="\$\{entry\.key\}"/);
        assert.match(workbench, /key: "rendered"/);
        assert.match(workbench, /key: "local"/);
        assert.match(workbench, /key: "raw"/);
        assert.match(workbench, /发送正文/);
        assert.match(workbench, /服务端原始正文/);
        assert.match(styles, /\.trust-reply-preview-tabs \{/);
        assert.match(styles, /\.trust-reply-preview-tab \{/);
        // I-7: tab switching must go through state + render(), never the host DOM.
        assert.doesNotMatch(workbench, /host\.querySelector\([^)]*preview/);
        // S-2: the preview block keeps the shared pre shape and carries no
        // inline style. The only inline style anywhere in the workbench source
        // is the pre-existing progress-bar width span — it sits in the return
        // template, not in the preview markup, and stays untouched.
        const inlineStyleLines = workbench.split("\n").filter((line) => line.includes('style="'));
        assert.strictEqual(inlineStyleLines.length, 1);
        assert.match(inlineStyleLines[0], /trust-reply-progress/);
        const previewMarkupStart = workbench.indexOf("const previewTabBar");
        const previewMarkupEnd = workbench.indexOf("const previewBlock =") + workbench.slice(workbench.indexOf("const previewBlock =")).indexOf("</div>`;");
        const previewMarkup = workbench.slice(previewMarkupStart, previewMarkupEnd);
        assert.doesNotMatch(previewMarkup, /style="/);
    });

    it("exposes per-card fact chips and picker without owner gating", () => {
        // 计划 02 (I-6): picker 不再产出 used/owner 门禁——其他摘要已选的事实仍
        // 显示「可添加」；本 request 已选与保存中仍 disabled。
        assert.match(workbench, /data-action="add-fact"/);
        assert.match(workbench, /data-action="remove-fact"/);
        assert.match(workbench, /data-action="toggle-fact-picker"/);
        assert.doesNotMatch(workbench, /已用于摘要 |"used"|factOwnerById/);
        assert.match(workbench, /已选择/);
        assert.match(workbench, /保存中/);
        assert.match(workbench, /class="trust-reply-fact-picker-option"/);
        assert.match(workbench, /optionState = "available"/);
        assert.doesNotMatch(workbench, /optionState = "used"/);
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

function bootstrapPayload(sourceType, sourceId, factIds, mismatchFactIds) {
    const mismatchIds = mismatchFactIds || [];
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
        rulesByCategory: [...factIds, ...mismatchIds].map((id) => ({ ruleId: id, displayName: `Fact ${id}`, answerBody: `body ${id}` })),
        requestCoverage: [{
            index: 0,
            requestKey: `${sourceType}-${sourceId}-request`,
            requestText: "Question",
            status: "GROUNDED",
            factRuleIds: [...factIds],
            // 计划 02 (I-2): server-side diagnostic fields, carried for the muted hint only.
            intentMatchedFactRuleIds: [...factIds],
            intentMismatchFactRuleIds: [...mismatchIds],
            allowedHandlings: ["ANSWER_WITH_EVIDENCE", "ANSWER_SUPPORTED_PART", "ANSWER_FACTS_VERBATIM", "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT", "ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"],
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

// ---- 计划 02 (I-2): intent-mismatch muted hint + never-sent-back diagnostics ----

describe("intent mismatch hints", () => {
    it("intent mismatches render the fixed muted hint under the fact section", async () => {
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

        const mismatchSpan = /<span class="muted" data-role="item-facts-dropped">([^<]*)<\/span>/.exec(host.innerHTML);
        assert.ok(mismatchSpan, "mismatch hint must render under the fact section");
        // S-1: 文案固定，不随事实 id/名称变化。
        assert.strictEqual(mismatchSpan[1], "人工选择已生效；系统未匹配到对应意图，已记录供后续优化。");
        // S-1 禁止项: 提示片段无 inline style，且未引入新 CSS class。
        assert.ok(!/style=/.test(mismatchSpan[0]));
        assert.ok(!host.innerHTML.includes("trust-reply-fact-dropped"));
    });

    it("no intent mismatches renders no hint", async () => {
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

    it("diagnostic fields are never sent back to the server", async () => {
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
            // 计划 02 (I-2): the canonical matrix carries ONLY requestKey + factRuleIds.
            assert.deepStrictEqual(Object.keys(selection).sort(), ["factRuleIds", "requestKey"]);
        });
    });
});

// ---- P2a: bound facts survive as chips + hint wording is bound-not-basis ----

describe("P2a bound vs evidence split", () => {
    it("bound facts render as chips", async () => {
        // P2a (S-2): coverage.factRuleIds 现由服务端产自 boundRuleIds（绑定集合），
        // 前端 chips 走既有 request.factRuleIds 路径自动显示绑定的（含被丢弃的）事实。
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({
                ok: true,
                status: 200,
                json: async () => bootstrapPayload("TRAINING_MAIL", 101, [10, 20], [10, 20])
            });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        assert.deepStrictEqual(renderedFactIds(host), ["10", "20"]);
    });

    it("mismatch hint wording is the fixed manual-authority text", async () => {
        // 计划 02 (S-1): 提示文案固定为「人工选择已生效；系统未匹配到对应意图，
        // 已记录供后续优化。」，不出现 P1/P2a 的旧措辞。
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({
                ok: true,
                status: 200,
                json: async () => bootstrapPayload("TRAINING_MAIL", 101, [], [10])
            });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        const mismatchSpan = /<span class="muted" data-role="item-facts-dropped">([^<]*)<\/span>/.exec(host.innerHTML);
        assert.ok(mismatchSpan, "mismatch hint must render under the fact section");
        assert.strictEqual(mismatchSpan[1], "人工选择已生效；系统未匹配到对应意图，已记录供后续优化。");
        assert.ok(!mismatchSpan[1].includes("已绑定但不会"), "the P2a bound-but-not-basis wording must be gone");
        assert.ok(!mismatchSpan[1].includes("未被采纳"), "the P1 rejected wording must be gone");
        assert.ok(!/style=/.test(mismatchSpan[0]));
    });
});

// ---- 计划 02 (I-3/I-6/I-4): 全开放 options、跨摘要复用、事实前置文案 ----

describe("计划 02 workbench openness", () => {
    it("renders all seven backend handling options for every coverage", async () => {
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload("TRAINING_MAIL", 101, [1]) });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        // I-3: 7 个后端 options 全部渲染，状态不参与过滤。
        const seven = [
            "ANSWER_WITH_EVIDENCE",
            "ANSWER_SUPPORTED_PART",
            "ANSWER_FACTS_VERBATIM",
            "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT",
            "ANSWER_FROM_OPERATOR_INPUT",
            "ACKNOWLEDGE_PENDING",
            "OMIT"
        ];
        seven.forEach((handling) => {
            assert.ok(host.innerHTML.includes(`<option value="${handling}"`), `option ${handling} must render`);
        });
        // 下拉不被状态置灰（只有 pending 才 disabled）。
        assert.ok(!/data-role="handling"[^>]*disabled/.test(host.innerHTML));
    });

    it("facts selected by another request remain addable in the picker", async () => {
        const twoRequestPayload = bootstrapPayload("TRAINING_MAIL", 101, [10]);
        twoRequestPayload.requestCoverage.push({
            index: 1,
            requestKey: "TRAINING_MAIL-101-request-2",
            requestText: "Second question",
            status: "UNSUPPORTED",
            factRuleIds: [],
            intentMatchedFactRuleIds: [],
            intentMismatchFactRuleIds: [],
            allowedHandlings: ["ANSWER_WITH_EVIDENCE", "ANSWER_SUPPORTED_PART", "ANSWER_FACTS_VERBATIM", "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT", "ANSWER_FROM_OPERATOR_INPUT", "ACKNOWLEDGE_PENDING", "OMIT"],
            recommendedHandling: "ANSWER_FROM_OPERATOR_INPUT"
        });
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({ ok: true, status: 200, json: async () => twoRequestPayload });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        // 打开第二个摘要的 picker：事实 10 已被 request-1 选择，仍显示「可添加」。
        host.dispatchEvent("click", event(actionButton("toggle-fact-picker", "TRAINING_MAIL-101-request-2")));
        await settle();

        const option = /<button type="button" class="trust-reply-fact-picker-option" data-action="add-fact" data-request-key="TRAINING_MAIL-101-request-2" data-fact-id="10" data-state="([^"]+)"[^>]*>([\s\S]*?)<\/button>/.exec(host.innerHTML);
        assert.ok(option, "fact 10 option must render for the second request");
        assert.strictEqual(option[1], "available", "cross-request fact must be available (I-6)");
        assert.ok(option[2].includes("可添加"), "cross-request fact must not be disabled/used (I-6)");
        assert.ok(!option[0].includes("disabled"), "cross-request fact option must not be disabled (I-6)");
    });

    it("TRUST_REPLY_FACT_REQUIRED renders 请先添加事实", async () => {
        const { window, document } = createSandbox((url, options) => {
            if (String(url).endsWith("/generations/stream")) {
                return Promise.resolve({
                    ok: true,
                    status: 200,
                    body: sseStream('event: error\ndata: {"code":"TRUST_REPLY_FACT_REQUIRED","message":"facts required"}\n\n')
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

        // I-4: 前端显示「请先添加事实」；handling 下拉保留当前选择（不重置）。
        assert.ok(host.innerHTML.includes("请先添加事实"));
        assert.ok(host.innerHTML.includes("facts required") === false, "raw error message must not leak");
        assert.ok(host.innerHTML.includes('<option value="ANSWER_WITH_EVIDENCE" selected'), "handling selection is retained");
    });
});

// ---- 计划 02: 依据+说明混合（S-1 渲染 / S-2 前置校验）----

describe("计划 02 blended handling", () => {
    function blendedPayload() {
        const payload = bootstrapPayload("TRAINING_MAIL", 101, [1]);
        payload.requestCoverage[0].status = "PARTIAL";
        payload.requestCoverage[0].allowedHandlings = [
            "ANSWER_SUPPORTED_PART",
            "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT",
            "ANSWER_FROM_OPERATOR_INPUT",
            "ACKNOWLEDGE_PENDING",
            "OMIT"
        ];
        payload.requestCoverage[0].recommendedHandling = "ANSWER_SUPPORTED_PART";
        return payload;
    }

    it("renders the blended option between supported part and operator input", async () => {
        const { window, document } = createSandbox((url, options) => {
            return Promise.resolve({ ok: true, status: 200, json: async () => blendedPayload() });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        assert.ok(
            host.innerHTML.includes('<option value="ANSWER_EVIDENCE_WITH_OPERATOR_INPUT">依据+说明混合</option>'),
            "the new handling must render as a data-driven option with the S-1 label"
        );
        const supportedIdx = host.innerHTML.indexOf('<option value="ANSWER_SUPPORTED_PART"');
        const blendedIdx = host.innerHTML.indexOf('<option value="ANSWER_EVIDENCE_WITH_OPERATOR_INPUT"');
        const operatorIdx = host.innerHTML.indexOf('<option value="ANSWER_FROM_OPERATOR_INPUT"');
        assert.ok(supportedIdx >= 0 && blendedIdx > supportedIdx && operatorIdx > blendedIdx,
            "option order must match the backend enum order (S-1)");
        // S-1 禁止项: 新选项无 inline style、无新 CSS class。
        assert.ok(
            !/<option value="ANSWER_EVIDENCE_WITH_OPERATOR_INPUT"[^>]*style=/.test(host.innerHTML),
            "the new option must not carry an inline style"
        );
        assert.ok(
            !/<option value="ANSWER_EVIDENCE_WITH_OPERATOR_INPUT"[^>]*class=/.test(host.innerHTML),
            "the new option must not carry a new CSS class"
        );
    });

    it("empty instruction with blended handling is blocked before any request", async () => {
        const calls = [];
        const { window, document } = createSandbox((url, options) => {
            calls.push(String(url));
            return Promise.resolve({ ok: true, status: 200, json: async () => {
                const payload = blendedPayload();
                payload.requestCoverage[0].recommendedHandling = "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT";
                return payload;
            } });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();

        // draftHandling 由 recommendedHandling 驱动 → 混合；说明框保持为空。
        host.dispatchEvent("click", event(actionButton("adjust-item", "TRAINING_MAIL-101-request")));
        await settle();

        assert.ok(host.innerHTML.includes("请先填写回答说明"), "precheck must set the operator-facing error");
        assert.ok(
            host.innerHTML.includes("回答说明（AI 将仅据此生成）"),
            "blended handling must keep the 'AI 将仅据此生成' instruction label (S-2)"
        );
        assert.ok(!host.innerHTML.includes('data-role="item-error"') ||
            host.innerHTML.includes('class="ai-reply-error"'), "error uses the existing ai-reply-error class only");
        // 前端拦下 → Network 里没有生成请求（只有 bootstrap 那次）。
        assert.deepStrictEqual(
            calls.filter((url) => url.endsWith("/generations/stream")),
            [],
            "no generation request may be sent when the instruction is empty (A-3)"
        );
    });
});

// ---- 计划 03: 按事实原文回答（T4.3）----

describe("计划 03 verbatim handling", () => {
    it("labels ANSWER_FACTS_VERBATIM as 按事实原文回答 in HANDLING_LABELS", () => {
        // S-1: HANDLING_LABELS 新增且仅新增一行，逐字等于契约。
        assert.match(workbench, /ANSWER_FACTS_VERBATIM:\s*"按事实原文回答"/);
        // 与 allowedHandlings 的排列顺序一致：在 ANSWER_SUPPORTED_PART 之后、
        // ANSWER_EVIDENCE_WITH_OPERATOR_INPUT 之前。
        const supportedIdx = workbench.indexOf('ANSWER_SUPPORTED_PART: "回答有依据部分"');
        const verbatimIdx = workbench.indexOf('ANSWER_FACTS_VERBATIM: "按事实原文回答"');
        const blendedIdx = workbench.indexOf('ANSWER_EVIDENCE_WITH_OPERATOR_INPUT: "依据+说明混合"');
        assert.ok(supportedIdx >= 0 && verbatimIdx > supportedIdx && blendedIdx > verbatimIdx,
            "HANDLING_LABELS order must match the backend enum order (S-1)");
    });

    it("keeps ANSWER_FACTS_VERBATIM out of OPERATOR_INSTRUCTION_HANDLINGS", () => {
        // I-8: verbatim 不调用 AI——绝不能进「必须非空回答说明」集合，
        // 否则生成前置校验会强制要求填说明（422 TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID）。
        const block = /OPERATOR_INSTRUCTION_HANDLINGS = Object\.freeze\(\[([\s\S]*?)\]\)/.exec(workbench);
        assert.ok(block, "OPERATOR_INSTRUCTION_HANDLINGS block must exist");
        assert.ok(!block[1].includes("ANSWER_FACTS_VERBATIM"),
            "verbatim handling must not be added to OPERATOR_INSTRUCTION_HANDLINGS (I-8)");
    });
});