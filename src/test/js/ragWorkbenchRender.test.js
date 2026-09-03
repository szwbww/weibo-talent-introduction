"use strict";

// 计划 05（c6）契约测试 —— 整封式 RAG 工作台渲染契约（G-8 / I-26 / I-27 / I-28 / I-29 / S-2..S-4）。
// 断言源：docs/plans/2026-09-02/05-workbench-frontend-replace.md T5 / 验收标准 / S-2..S-4。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const workbenchPath = path.join(root, "trust-reply-workbench.js");
const stylesPath = path.join(root, "styles.css");
const indexPath = path.join(root, "index.html");
const workbenchSource = fs.readFileSync(workbenchPath, "utf-8");
const styles = fs.readFileSync(stylesPath, "utf-8");
const html = fs.readFileSync(indexPath, "utf-8");

const CACHE_KEY = "20260902-rag-workbench";

// —— S-2..S-4 契约栅格（与计划文件代码栅格逐字节一致，追加在 styles.css EOF）——
const CSS_S2 = `.trust-reply-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 350px;
    gap: 13px;
    align-items: start;
}

@media (max-width: 1100px) {
    .trust-reply-layout {
        grid-template-columns: 1fr;
    }
}

.trust-reply-doc {
    padding: 16px 18px;
    max-height: 620px;
    overflow: auto;
}

.trust-reply-para {
    position: relative;
    padding: 7px 11px 7px 12px;
    border-radius: 6px;
    font-size: 13px;
    line-height: 1.8;
    white-space: pre-wrap;
    margin-bottom: 9px;
    border-left: 3px solid var(--border);
}

.trust-reply-para.verbatim {
    border-left-color: var(--verbatim);
    background: var(--verbatim-bg);
}

.trust-reply-para.frame {
    border-left-style: dashed;
    color: var(--text-muted);
    background: var(--surface);
}

.trust-reply-para-tag {
    position: absolute;
    right: 9px;
    top: 5px;
    font-size: 9.5px;
    opacity: 0;
    transition: opacity 0.12s;
    color: var(--text-muted);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.trust-reply-para:hover .trust-reply-para-tag {
    opacity: 1;
}

.trust-reply-para.verbatim .trust-reply-para-tag {
    color: var(--verbatim);
}

.trust-reply-para.edited {
    border-left-color: var(--error);
    background: var(--error-bg);
}`;

const CSS_S3 = `.trust-reply-facts {
    padding: 10px 13px;
}

.trust-reply-fact {
    display: flex;
    align-items: flex-start;
    gap: 7px;
    padding: 7px 0;
    border-bottom: 1px dashed var(--line);
    font-size: 12px;
}

.trust-reply-fact:last-child {
    border-bottom: none;
}

.trust-reply-fact-index {
    color: var(--text-muted);
    font-size: 10.5px;
    width: 15px;
    flex: none;
    padding-top: 2px;
}

.trust-reply-fact-main {
    min-width: 0;
    flex: 1;
}

.trust-reply-fact-code {
    color: var(--primary);
    font-weight: 600;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 11px;
}

.trust-reply-fact-remove {
    color: var(--text-muted);
    cursor: pointer;
    font-size: 14px;
    line-height: 1;
    padding: 2px 3px;
    flex: none;
    background: none;
    border: none;
}

.trust-reply-fact-remove:hover {
    color: var(--error);
}

.trust-reply-fact-add {
    margin-top: 9px;
    display: flex;
    gap: 6px;
}

.trust-reply-fact-add input {
    flex: 1;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 5px 9px;
    font-size: 12px;
    font-family: inherit;
    background: var(--surface);
}

.trust-reply-unaddressed {
    padding: 10px 13px;
}

.trust-reply-unaddressed-item {
    background: var(--warning-bg);
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
    padding: 9px 11px;
    font-size: 11.5px;
    color: var(--warning);
    line-height: 1.65;
    margin-bottom: 7px;
}

.trust-reply-unaddressed-item:last-child {
    margin-bottom: 0;
}

.trust-reply-unaddressed-why {
    color: var(--text-muted);
    font-size: 10.5px;
    display: block;
    margin-top: 3px;
}

.trust-reply-unaddressed:empty::before {
    content: "无";
    color: var(--text-muted);
    font-size: 12px;
}`;

const CSS_S4 = `.trust-reply-frame-bar {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 9px 13px;
    border-bottom: 1px solid var(--line);
    background: var(--surface);
    flex-wrap: wrap;
    font-size: 11.5px;
}

.trust-reply-frame-bar select {
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 3px 7px;
    font-size: 11.5px;
    font-family: inherit;
    background: #fff;
    max-width: 190px;
}

.trust-reply-send {
    position: sticky;
    bottom: 0;
    background: rgba(255, 255, 255, .96);
    backdrop-filter: blur(8px);
    border: 1px solid var(--border);
    border-top-width: 2px;
    border-radius: var(--radius-md);
    padding: 11px 13px;
    margin-top: 13px;
    display: flex;
    align-items: center;
    gap: 10px;
}`;

function stripWs(text) {
    return text.replace(/\s+/g, " ").trim();
}

function ruleBlock(css, selector) {
    const start = css.indexOf(`${selector} {`);
    assert.ok(start >= 0, `missing rule ${selector}`);
    const end = css.indexOf("\n}", start);
    assert.ok(end > start, `unterminated rule ${selector}`);
    return css.slice(start, end + 2);
}

// —— 渲染载荷桩（与 RagComposeResult 同形：frame / bodyParagraphs / usedFacts / unaddressed /
// corpusFingerprint；03 契约字段名固定）——
function composePayload(overrides) {
    return Object.assign({
        frame: {
            selection: { salutationSnippetId: null, greetingSnippetId: null, ackSnippetId: null, closingSnippetId: null },
            version: "v1",
            salutation: "Dear Professor Tanaka,",
            greeting: "Thank you for your message.",
            acknowledgement: "I look forward to your reply.",
            closing: "Kind regards,\nWu Wei"
        },
        bodyParagraphs: [
            { text: "This programme is jointly funded by the province.", renderMode: "COMPOSE" },
            { text: "The official talent programme covers relocation support for three years.", renderMode: "VERBATIM" }
        ],
        usedFacts: [
            { factCode: "KB-FUND-034", title: "资助与经费说明", renderMode: "VERBATIM", riskLevel: "LOW", status: "APPROVED", origin: "MODEL" },
            { factCode: "KB-COMM-010", title: "申报流程要求", renderMode: "COMPOSE", riskLevel: "LOW", status: "APPROVED", origin: "MANDATORY" }
        ],
        unaddressed: [
            { quote: "Could you share the salary range?", reason: "来信问了薪资，语料中没有对应事实，未作答。" }
        ],
        modelCoverage: [],
        warnings: [],
        corpusFingerprint: "e62421a42c432cf3",
        retrievalUsage: null,
        generationUsage: null
    }, overrides || {});
}

function snippetPayload() {
    return [
        { id: 1, snippetType: "SALUTATION", content: "Dear Professor,\nsecond line", displayOrder: 1, enabled: true },
        { id: 2, snippetType: "CLOSING", content: "Kind regards,\nWu Wei", displayOrder: 1, enabled: true }
    ];
}

function makeWindow(fetchImpl, confirmImpl) {
    return {
        document: { querySelectorAll: () => [], querySelector: () => null },
        fetch: fetchImpl,
        confirm: confirmImpl,
        AbortController,
        navigator: { clipboard: { writeText: () => Promise.resolve() } },
        setTimeout,
        clearTimeout
    };
}

class FakeElement {
    constructor() {
        this._innerHTML = "";
        this.listeners = new Map();
        this.inputValues = {};
    }
    set innerHTML(value) {
        this._innerHTML = String(value);
    }
    get innerHTML() {
        return this._innerHTML;
    }
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
    listenerCount() {
        let count = 0;
        for (const list of this.listeners.values()) count += list.length;
        return count;
    }
    querySelector(selector) {
        const role = /data-role="([^"]+)"/.exec(selector);
        if (role) return { value: this.inputValues[role[1]] || "" };
        return null;
    }
}

function createSandbox(fetchImpl, confirmImpl) {
    const window = makeWindow(fetchImpl, confirmImpl === undefined ? () => true : confirmImpl);
    const sandbox = { window, console, setTimeout, clearTimeout, AbortController };
    vm.createContext(sandbox);
    vm.runInContext(workbenchSource, sandbox);
    return { window, sandbox };
}

// 构造可被委托 click 处理器识别的按钮 target。
function actionTarget(action, extra) {
    const target = {
        dataset: Object.assign({ action }, extra || {}),
        closest(selector) {
            return selector === "[data-action]" ? this : null;
        }
    };
    return target;
}

// 可被 onInputEvent / onFocusOutEvent 识别的段落 target。
function paraTarget(index, text) {
    const target = {
        dataset: { paraIndex: String(index) },
        textContent: text,
        classList: { add() {} },
        closest(selector) {
            return selector === ".trust-reply-para" ? this : null;
        }
    };
    return target;
}

function flush(times) {
    const steps = times || 6;
    let chain = Promise.resolve();
    for (let step = 0; step < steps; step += 1) {
        chain = chain.then(() => new Promise((resolve) => setImmediate(resolve)));
    }
    return chain;
}

// 可推迟完成的 fetch 桩：compose 请求被捕获，由测试决定何时以何载荷返回。
function deferredComposeFetch(composeResponses, snippets) {
    const calls = [];
    const pending = [];
    const impl = (url, options) => {
        const path = String(url);
        if (path.endsWith("/api/rag-reply/compose")) {
            const call = { url: path, options, body: JSON.parse(options.body) };
            calls.push(call);
            return new Promise((resolve) => pending.push(resolve));
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => (snippets || snippetPayload()) });
    };
    const settleAll = async (index) => {
        const resolve = pending.shift();
        if (!resolve) return;
        const payload = composeResponses[index] === undefined
            ? composePayload()
            : (typeof composeResponses[index] === "function" ? composeResponses[index]() : composeResponses[index]);
        resolve({ ok: true, status: 200, json: async () => payload });
        await flush();
    };
    return { impl, calls, settleAll };
}

describe("rag workbench render contracts (计划 05)", () => {
    it("G-8: index.html keeps the shared workbench script include and the G-5 single key", () => {
        const match = html.match(/src="trust-reply-workbench\.js\?v=([^"]+)"/);
        assert.ok(match, "index.html must include trust-reply-workbench.js?v=");
        const keys = [...html.matchAll(/\?v=([0-9a-z-]+)/g)].map((item) => item[1]);
        assert.strictEqual(keys.length, 3, "three cache-busted assets expected");
        assert.ok(keys.every((key) => key === CACHE_KEY), `all keys must equal ${CACHE_KEY}`);
    });

    it("I-26: a VERBATIM body paragraph renders class trust-reply-para verbatim from renderMode only", () => {
        const { window } = createSandbox(() => Promise.resolve({ ok: true, status: 200, json: async () => ({}) }));
        const runtime = window.TrustReplyWorkbench;
        assert.strictEqual(typeof runtime.mount, "function");
        const verbatim = runtime.renderParagraph({ kind: "body", renderMode: "VERBATIM", text: "原文段落", index: 0 });
        assert.ok(verbatim.includes('class="trust-reply-para verbatim"'), "VERBATIM renderMode must drive the class");
        assert.ok(verbatim.includes(">逐字</span>") || verbatim.includes("逐字"), "verbatim tag must be present");
        const compose = runtime.renderParagraph({ kind: "body", renderMode: "COMPOSE", text: "模型产出", index: 1 });
        assert.ok(compose.includes('class="trust-reply-para"'), "COMPOSE renders the plain class");
        assert.ok(!compose.includes("verbatim"), "COMPOSE must not carry verbatim");
        const frame = runtime.renderParagraph({ kind: "frame", slotLabel: "尊语", text: "Dear Prof,", index: undefined });
        assert.ok(frame.includes('class="trust-reply-para frame"'), "frame paragraphs must carry frame class");
        // I-26 第二半：渲染逻辑不得按正文文本比对 answer（验收 grep 为空）。
        assert.ok(!/answer\s*===/.test(workbenchSource), "renderer must never text-compare against answers");
    });

    it("I-27: dirty=true regenerate calls the injected confirm and skips the request on cancel", async () => {
        const deferred = deferredComposeFetch([composePayload(), composePayload()]);
        const confirms = [];
        const { window } = createSandbox(deferred.impl, () => {
            confirms.push(1);
            return false;
        });
        const host = new FakeElement();
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            autoBootstrap: false,
            onComplete: async () => {}
        });
        assert.ok(instance && typeof instance.unmount === "function");

        // 第一次重新生成 → 返回草稿。
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await deferred.settleAll(0);
        assert.strictEqual(deferred.calls.length, 1, "first compose must fire");

        // 进入编辑并手改第 0 段（focusout 提交）。
        host.dispatchEvent("click", { target: actionTarget("edit-body") });
        const editedText = "This programme is jointly funded by the province (edited by operator).";
        host.dispatchEvent("input", { target: paraTarget(0, editedText) });
        host.dispatchEvent("focusout", { target: paraTarget(0, editedText) });
        await flush();
        assert.ok(host.innerHTML.includes("已手工编辑"), "dirty state must render a visible marker");
        assert.ok(host.innerHTML.includes('data-role="dirty-flag"'), "dirty flag element must exist");

        // dirty 后点重新生成：confirm 返回 false → 不发请求、保留手改。
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await flush();
        assert.strictEqual(confirms.length, 1, "regenerate must call confirm when dirty");
        assert.strictEqual(deferred.calls.length, 1, "cancel must not fire a compose request");
        assert.ok(host.innerHTML.includes("(edited by operator)"), "cancel must keep the manual edits");

        // 确认返回 true（切换 window.confirm 行为）。
        const confirmImpl = window.confirm;
        window.confirm = () => {
            confirms.push(1);
            return true;
        };
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await deferred.settleAll(1);
        assert.strictEqual(deferred.calls.length, 2, "confirmed regenerate must fire a compose request");
        assert.ok(!host.innerHTML.includes("(edited by operator)"), "confirmed regenerate discards manual edits");
        window.confirm = confirmImpl;
    });

    it("I-28: removeFact only mutates request params, never the local paragraph array", async () => {
        const { window } = createSandbox(() => Promise.resolve({ ok: true, status: 200, json: async () => ({}) }));
        const runtime = window.TrustReplyWorkbench;
        // 纯函数路径：状态上的段落数组长度不变，参数表追加 excludedFactCodes。
        const state = {
            paragraphs: [{ kind: "body", text: "p1" }, { kind: "body", text: "p2" }],
            forcedFactCodes: ["KB-FUND-034"],
            excludedFactCodes: []
        };
        runtime.applyRemoveFact(state, "KB-FUND-034");
        assert.strictEqual(state.paragraphs.length, 2, "removeFact must never locally splice paragraphs");
        assert.deepStrictEqual(state.excludedFactCodes, ["KB-FUND-034"], "code must move to excludedFactCodes");
        assert.deepStrictEqual(state.forcedFactCodes, [], "code must leave forcedFactCodes");
        const request = runtime.buildComposeRequest(Object.assign({ source: { sourceType: "LIVE_INBOUND", sourceId: 1 }, model: "DEEPSEEK_V4_FLASH", frameSelection: {} }, state));
        assert.ok(request.excludedFactCodes.includes("KB-FUND-034"), "next request body must carry excludedFactCodes");
    });

    it("I-28 (flow): clicking × re-composes with excludedFactCodes and paragraphs length stays stable", async () => {
        const deferred = deferredComposeFetch([
            composePayload(),
            () => composePayload({ usedFacts: [
                { factCode: "KB-COMM-010", title: "申报流程要求", renderMode: "COMPOSE", riskLevel: "LOW", status: "APPROVED", origin: "MANDATORY" }
            ], bodyParagraphs: [
                { text: "This programme is jointly funded by the province.", renderMode: "COMPOSE" },
                { text: "The official talent programme covers relocation support for three years.", renderMode: "VERBATIM" }
            ] })
        ]);
        const { window } = createSandbox(deferred.impl);
        const host = new FakeElement();
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 601 },
            contextPath: "",
            autoBootstrap: false,
            onComplete: async () => {}
        });
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await deferred.settleAll(0);
        assert.strictEqual(deferred.calls.length, 1);
        assert.ok(host.innerHTML.includes("KB-FUND-034"), "first compose renders the fact row");
        assert.strictEqual((host.innerHTML.match(/data-para-index=/g) || []).length, 2, "two body paragraphs rendered");

        host.dispatchEvent("click", { target: actionTarget("remove-fact", { code: "KB-FUND-034" }) });
        assert.strictEqual(deferred.calls.length, 2, "remove-fact must trigger an immediate re-compose");
        assert.deepStrictEqual(deferred.calls[1].body.excludedFactCodes, ["KB-FUND-034"], "re-compose body excludes the code");
        assert.strictEqual((host.innerHTML.match(/data-para-index=/g) || []).length, 2, "pending state keeps the previous paragraphs untouched");
        await deferred.settleAll(1);
        assert.ok(!host.innerHTML.includes("KB-FUND-034"), "fact disappears from the right column after re-compose");
        assert.strictEqual((host.innerHTML.match(/data-para-index=/g) || []).length, 2, "paragraph count unchanged after re-compose");
    });

    it("I-29: send stays enabled with unaddressed questions present", async () => {
        const deferred = deferredComposeFetch([
            composePayload({ unaddressed: [
                { quote: "q1", reason: "r1" },
                { quote: "q2", reason: "r2" },
                { quote: "q3", reason: "r3" }
            ] })
        ]);
        const { window } = createSandbox(deferred.impl);
        const host = new FakeElement();
        window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 601 },
            contextPath: "",
            autoBootstrap: false,
            onComplete: async () => {}
        });
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await deferred.settleAll(0);
        assert.ok(host.innerHTML.includes('data-role="unaddressed-note"'), "unaddressed note must render");
        assert.ok(host.innerHTML.includes("未识别提问 3 项"), "note must carry the count");
        const completeButton = host.innerHTML.match(/data-action="complete"[^>]*>/);
        assert.ok(completeButton, "complete button must exist");
        assert.ok(!completeButton[0].includes("disabled"), "unaddressed questions must never disable send (I-29)");
    });

    it("S-2..S-4: new class CSS blocks match the contract verbatim and send bar never uses --panel-bg", () => {
        assert.ok(styles.includes(CSS_S2), "S-2 CSS block must be byte-identical in styles.css");
        assert.ok(styles.includes(CSS_S3), "S-3 CSS block must be byte-identical in styles.css");
        assert.ok(styles.includes(CSS_S4), "S-4 CSS block must be byte-identical in styles.css");
        const send = ruleBlock(styles, ".trust-reply-send");
        assert.ok(send.includes("rgba(255, 255, 255, .96)"), ".trust-reply-send background must be literal rgba white");
        assert.ok(send.includes("backdrop-filter: blur(8px)"), ".trust-reply-send must keep backdrop blur");
        assert.ok(!send.includes("var(--panel-bg)"), ".trust-reply-send must never use the translucent --panel-bg");
        // 契约禁止 inline style。
        assert.ok(!/style=/.test(workbenchSource), "workbench must not emit inline styles");
    });
});
