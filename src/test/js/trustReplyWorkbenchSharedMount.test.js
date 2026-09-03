"use strict";

// 计划 05（c6）改写 —— 只保留三组断言（G-7）：
// 1) I-24 挂载契约（window.TrustReplyWorkbench.mount / instance.unmount / options 键集合不变）；
// 2) G-5 缓存键三联同值（20260902-rag-workbench）；
// 3) I-25 unmount 语义：abort 全部在途请求、解绑全部监听器、late response 不写宿主。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const workbenchPath = path.join(root, "trust-reply-workbench.js");
const indexPath = path.join(root, "index.html");
const appPath = path.join(root, "app.js");
const source = fs.readFileSync(workbenchPath, "utf-8");
const appSource = fs.readFileSync(appPath, "utf-8");
const indexSource = fs.readFileSync(indexPath, "utf-8");

const CACHE_KEY = "20260902-rag-workbench";
// I-24：options 键集合（顺序无关）—— 两个宿主与运行时都不得改名/改必填性。
const OPTION_KEYS = ["mode", "source", "contextPath", "autoBootstrap", "onUnauthorized", "onChange", "onComplete"];

function composePayload() {
    return {
        frame: {
            selection: { salutationSnippetId: null, greetingSnippetId: null, ackSnippetId: null, closingSnippetId: null },
            version: "v1",
            salutation: "Dear Professor,",
            greeting: "Thank you for your message.",
            acknowledgement: "I look forward to your reply.",
            closing: "Kind regards,\nWu Wei"
        },
        bodyParagraphs: [
            { text: "The programme is jointly funded.", renderMode: "COMPOSE" },
            { text: "Relocation support covers three years.", renderMode: "VERBATIM" }
        ],
        usedFacts: [{ factCode: "KB-FUND-034", title: "资助说明", renderMode: "VERBATIM", riskLevel: "LOW", status: "APPROVED", origin: "MODEL" }],
        unaddressed: [],
        modelCoverage: [],
        warnings: [],
        corpusFingerprint: "e62421a42c432cf3",
        retrievalUsage: null,
        generationUsage: null
    };
}

class FakeElement {
    constructor() {
        this._innerHTML = "";
        this.listeners = new Map();
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
    querySelector() { return null; }
}

function makeWindow(fetchImpl) {
    return {
        document: { querySelectorAll: () => [], querySelector: () => null },
        fetch: fetchImpl,
        confirm: () => true,
        AbortController,
        navigator: { clipboard: { writeText: () => Promise.resolve() } },
        setTimeout,
        clearTimeout
    };
}

function createSandbox(fetchImpl) {
    const window = makeWindow(fetchImpl);
    const sandbox = { window, console, setTimeout, clearTimeout, AbortController };
    vm.createContext(sandbox);
    vm.runInContext(source, sandbox);
    return { window, sandbox };
}

function flush() {
    return new Promise((resolve) => setImmediate(() => setImmediate(() => setImmediate(resolve))));
}

function actionTarget(action) {
    return {
        dataset: { action },
        closest(selector) {
            return selector === "[data-action]" ? this : null;
        }
    };
}

// 提取 mount 调用处 8 空格缩进的顶层 option 键
// （source: { sourceType… } 等嵌套键不会命中；contextPath 是属性简写，单独处理）。
function optionKeysInBlock(block) {
    const mountCall = block.indexOf("runtime.mount(host, {");
    assert.ok(mountCall >= 0, "site must call runtime.mount(host, {");
    const close = block.indexOf("});", mountCall);
    assert.ok(close > mountCall, "mount options must close");
    const optionsText = block.slice(mountCall, close + 3);
    const keys = new Set();
    for (const match of optionsText.matchAll(/^ {8}([A-Za-z][A-Za-z0-9]*)\s*[:,]/gm)) {
        keys.add(match[1]);
    }
    if (/\bcontextPath\s*,/.test(optionsText)) keys.add("contextPath");
    return keys;
}

describe("shared trust reply workbench mount contract (计划 05 改写)", () => {
    it("I-24: mount is exposed with an unmount-returning instance and renders the workbench shell", async () => {
        const { window } = createSandbox(() => new Promise(() => {}));
        assert.strictEqual(typeof window.TrustReplyWorkbench.mount, "function", "window.TrustReplyWorkbench.mount must exist");
        const host = new FakeElement();
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 601 },
            contextPath: "",
            autoBootstrap: false,
            onUnauthorized: () => {},
            onChange: () => {},
            onComplete: async () => {}
        });
        assert.ok(instance && typeof instance.unmount === "function", "mount must return an instance with unmount");
        assert.ok(host.innerHTML.includes('class="trust-reply-workbench"'), "workbench root must render into the host");
        assert.ok(host.innerHTML.includes('data-role="draft"'), "three-block draft container must render");
        assert.ok(host.innerHTML.includes('data-role="facts"'), "facts block must render");
        assert.ok(host.innerHTML.includes('data-role="unaddressed"'), "unaddressed block must render");
        instance.unmount();
        assert.strictEqual(host.listenerCount(), 0, "unmount must unbind every delegated listener");
    });

    it("I-24: app.js keeps both mount call sites with the unchanged option key set", () => {
        const training = appSource.indexOf("function mountAiTrainingTrustReply(mail)");
        const live = appSource.indexOf("function mountLiveTrustReply(recordId)");
        assert.ok(training >= 0, "mountAiTrainingTrustReply must exist in app.js");
        assert.ok(live >= 0, "mountLiveTrustReply must exist in app.js");
        const trainingKeys = optionKeysInBlock(appSource.slice(training, training + 1500));
        const liveKeys = optionKeysInBlock(appSource.slice(live, live + 1500));
        const union = new Set([...trainingKeys, ...liveKeys]);
        // 不得出现任何契约之外的键（改名/新增即违例）；可选键允许按宿主取舍。
        for (const key of union) {
            assert.ok(OPTION_KEYS.includes(key), `unexpected option key ${key} at a mount call site`);
        }
        for (const key of OPTION_KEYS) {
            assert.ok(union.has(key), `option key ${key} must appear at one of the two mount call sites`);
        }
        // 运行时消费同一组键：实例工厂逐键读取。
        for (const key of OPTION_KEYS) {
            assert.ok(source.includes(`options.${key}`), `workbench must consume options.${key}`);
        }
        assert.ok(appSource.includes("window.TrustReplyWorkbench"), "runtime lookup must stay on the shared namespace");
        assert.ok(!/src="\/trust-reply-workbench\.js/.test(indexSource), "script include must stay context-relative");
    });

    it("G-5: the cache-key triad is one value (20260902-rag-workbench)", () => {
        const keys = [...indexSource.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 3, "index.html must carry exactly three cache-busted asset URLs");
        assert.strictEqual(new Set(keys).size, 1, "all three keys must share one value");
        assert.strictEqual(keys[0], CACHE_KEY, `the shared key must be ${CACHE_KEY}`);
    });

    it("I-25: unmount aborts the in-flight compose and no late response rewrites the host", async () => {
        const pending = [];
        const fetchImpl = (url) => {
            if (String(url).endsWith("/api/rag-reply/compose")) {
                return new Promise((resolve) => pending.push(resolve));
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => [] });
        };
        const { window } = createSandbox(fetchImpl);
        const host = new FakeElement();
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 601 },
            contextPath: "",
            autoBootstrap: false,
            onComplete: async () => {}
        });
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await flush();
        assert.strictEqual(pending.length, 1, "one compose must be in flight");
        const snapshot = host.innerHTML;
        instance.unmount();
        assert.strictEqual(host.listenerCount(), 0, "unmount must unbind every delegated listener");
        pending.shift()({ ok: true, status: 200, json: async () => composePayload() });
        await flush();
        assert.strictEqual(host.innerHTML, snapshot, "a late response must never rewrite an unmounted host");
    });

    it("I-25: unmount aborts the request signal so the fetch is cancelled", async () => {
        const captured = [];
        const fetchImpl = (url, options) => {
            if (String(url).endsWith("/api/rag-reply/compose")) {
                captured.push(options.signal);
                return new Promise((resolve, reject) => {
                    options.signal.addEventListener("abort", () => {
                        const error = new Error("aborted");
                        error.name = "AbortError";
                        reject(error);
                    });
                    void resolve;
                });
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => [] });
        };
        const { window } = createSandbox(fetchImpl);
        const host = new FakeElement();
        const instance = window.TrustReplyWorkbench.mount(host, {
            mode: "LIVE",
            source: { sourceType: "LIVE_INBOUND", sourceId: 601 },
            contextPath: "",
            autoBootstrap: false,
            onComplete: async () => {}
        });
        host.dispatchEvent("click", { target: actionTarget("regenerate") });
        await flush();
        assert.strictEqual(captured.length, 1, "compose fetch must carry an AbortSignal");
        assert.strictEqual(captured[0].aborted, false);
        instance.unmount();
        await flush();
        assert.strictEqual(captured[0].aborted, true, "unmount must abort the in-flight request");
    });
});
