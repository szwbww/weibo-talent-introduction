const fs = require("fs");
const path = require("path");
const assert = require("assert");
const vm = require("vm");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const stylesPath = path.join(root, "styles.css");
const workbenchPath = path.join(root, "trust-reply-workbench.js");
const indexPath = path.join(root, "index.html");
const styles = fs.readFileSync(stylesPath, "utf-8");
const workbench = fs.readFileSync(workbenchPath, "utf-8");
const html = fs.readFileSync(indexPath, "utf-8");

const CACHE_KEY = "20260821-v11-reply-subject-prefill";

// Source-text helpers (whitespace-normalized for the verbatim contract checks).
function stripWs(text) {
    return text.replace(/\s+/g, " ").trim();
}

function ruleBlock(css, selector) {
    const match = css.match(new RegExp(selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\s*\\{[^}]*\\}"));
    assert.ok(match, `expected a rule block for ${selector}`);
    return match[0];
}

function fnSource(source, name, nextName) {
    const start = source.indexOf(`function ${name}(`);
    const end = source.indexOf(`function ${nextName}(`);
    assert.ok(start >= 0 && end > start, `cannot slice function ${name}`);
    return source.slice(start, end);
}

function actionButton(action, requestKey) {
    return {
        dataset: requestKey ? { action, requestKey } : { action },
        closest(selector) {
            if (selector === "[data-action]") return this;
            return null;
        }
    };
}

function event(target, extra) {
    return { target, preventDefault() {}, stopPropagation() {}, ...extra };
}

function bootstrapPayload() {
    return {
        source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
        sourceVersion: "TRAINING_MAIL-101-v1",
        inboundSubject: "subject",
        inboundText: "body",
        expertName: "Expert",
        expertEmail: "expert@example.com",
        llmEnabled: true,
        availableModels: ["DEEPSEEK_V4_FLASH"],
        defaultModel: "DEEPSEEK_V4_FLASH",
        suggestedFactIds: [1, 2],
        canonicalFactIds: [1, 2],
        rulesByCategory: [1, 2].map((id) => ({ ruleId: id, displayName: `Fact ${id}`, answerBody: `body ${id}` })),
        requestCoverage: [{
            index: 0,
            requestKey: "TRAINING_MAIL-101-request",
            requestText: "Question",
            status: "GROUNDED",
            factRuleIds: [1, 2],
            droppedFactRuleIds: [],
            allowedHandlings: ["ANSWER_WITH_EVIDENCE", "OMIT"],
            recommendedHandling: "ANSWER_WITH_EVIDENCE"
        }],
        draftReadiness: "READY",
        contextWarnings: [],
        evidenceSetVersion: "TRAINING_MAIL-101-e1"
    };
}

// A locked bootstrap: the one GROUNDED request arrives already resolved, so
// assemble() skips item generation and enters the ASSEMBLING pending state.
function lockedBootstrapPayload() {
    const payload = bootstrapPayload();
    payload.savedState = {
        stateVersion: 7,
        status: "RESTORED",
        lockedItems: [{
            requestKey: "TRAINING_MAIL-101-request",
            versionId: "v-1",
            handling: "ANSWER_WITH_EVIDENCE",
            answerText: "generated answer",
            operatorInstruction: "",
            claims: [],
            model: "DEEPSEEK_V4_FLASH",
            generationKind: "AI_GENERATED",
            evidenceSetVersion: "TRAINING_MAIL-101-e1",
            sourceVersion: "TRAINING_MAIL-101-v1",
            operatorInstructionHash: "",
            contextVersion: "ctx-1"
        }]
    };
    return payload;
}

// A stream that never enqueues and never closes keeps the generation pending:
// reader.read() stays unresolved, so state.generation.pending stays true and
// the busy overlay stays rendered (I-1: the mask survives re-renders).
function neverResolvingStream() {
    return new ReadableStream({ start() { /* keep open */ } });
}

// vm + FakeElement harness, same shape as trustReplyWorkbench.test.js.
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
    querySelector() { return null; }
}

class FakeDocument {
    constructor() {
        this.activeElement = null;
    }
    createElement() { return new FakeElement(this); }
}

function settle() {
    return new Promise((resolve) => setImmediate(() => setImmediate(resolve)));
}

// The overlay is the last child of .reply-workflow-content, so everything
// from its class token up to </details> is the overlay fragment plus the
// two closing tags — no other component markup.
function overlayFragment(html) {
    const start = html.indexOf('class="trust-reply-busy-overlay"');
    assert.ok(start >= 0, "overlay must be rendered");
    return html.slice(start, html.indexOf("</details>"));
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
    vm.runInContext(workbench, sandbox);
    return { window, document };
}

describe("P2 overlay + dialog contrast (source contract)", () => {
    it("I-1: the overlay is a renderMarkup product, not an appendChild side effect", () => {
        assert.strictEqual((workbench.match(/renderBusyOverlay\(\)/g) || []).length, 2,
            "renderBusyOverlay must be defined once and interpolated exactly once inside renderMarkup");
        assert.ok(!workbench.includes("createElement"),
            "the overlay must not be attached via document.createElement/appendChild");
        const shell = fnSource(workbench, "renderShell", "render");
        assert.ok(!shell.includes("renderBusyOverlay"), "renderShell must not carry the overlay");
    });

    it("I-2: the overlay is the last child of .reply-workflow-content, never above <summary>", () => {
        const markup = fnSource(workbench, "renderMarkup", "renderToolbar");
        assert.ok(markup.includes('</section>${renderBusyOverlay()}</div>'),
            "the overlay interpolation must sit between the frame section close and the .reply-workflow-content close");
        assert.ok(markup.includes('<div class="reply-workflow-content"${busyOverlayState() ? \' aria-busy="true"\' : \'\'}>'),
            "aria-busy must be computed on the .reply-workflow-content container");
        const summaryIdx = markup.indexOf("<summary");
        assert.ok(summaryIdx >= 0, "renderMarkup must contain <summary>");
        assert.ok(!markup.slice(0, summaryIdx).includes("trust-reply-busy-overlay"),
            "no overlay token may precede the <summary> line (summary stays clickable, I-2)");
    });

    it("I-3: the only new absolutely positioned element is the overlay", () => {
        assert.strictEqual((styles.match(/position:\s*absolute/g) || []).length, 29,
            "position: absolute count must be the pre-change 28 plus exactly 1 (the overlay)");
        const overlay = ruleBlock(styles, ".trust-reply-busy-overlay");
        assert.ok(stripWs(overlay).includes("position: absolute; inset: 0; z-index: 6;"),
            "the overlay must be the absolutely positioned child of .reply-workflow-content");
    });

    it("I-4: the overlay cancel button reuses the delegated cancel-generation action", () => {
        assert.strictEqual((workbench.match(/data-action="cancel-generation"/g) || []).length, 2,
            "exactly two cancel buttons: toolbar + overlay");
        const onClick = fnSource(workbench, "onClick", "onChange");
        assert.strictEqual((onClick.match(/if \(action === "cancel-generation"\)/g) || []).length, 1,
            "the delegated onClick handler must keep its single cancel-generation branch");
        assert.ok(onClick.includes('event.target.closest("[data-action]")'), "delegation entry must be intact");
        const overlay = fnSource(workbench, "renderBusyOverlay", "renderToolbar");
        assert.ok(overlay.includes('data-action="cancel-generation"'), "overlay button must reuse the action");
    });

    it("I-5: busyOverlayState mirrors factActionBlockReason's first five branches, then completePending", () => {
        const busy = fnSource(workbench, "busyOverlayState", "renderBusyOverlay");
        const conditions = [...busy.matchAll(/if\s*\(([\s\S]*?)\)\s*\{/g)].map((m) => stripWs(m[1]));
        assert.strictEqual(conditions.length, 6, "busyOverlayState must have exactly six if branches");
        assert.deepStrictEqual(conditions, [
            "state.requests.some((request) => request.pending)",
            "state.factChangePending",
            "state.stateSavePending",
            "state.generation.pending",
            "state.frameSavePending",
            "state.completePending"
        ], "branch order must mirror the factActionBlockReason priority table + completePending");
        const fact = fnSource(workbench, "factActionBlockReason", "factActionReasonFor");
        const factConditions = [...fact.matchAll(/if\s*\(([^)]+)\)/g)].map((m) => m[1].replace(/\s+/g, " ").trim());
        assert.deepStrictEqual(factConditions, [
            "flags && flags.requestPending",
            "flags && flags.factChangePending",
            "flags && flags.stateSavePending",
            "flags && flags.generationPending",
            "flags && flags.frameSavePending"
        ], "factActionBlockReason must keep its five-branch priority table verbatim");
    });

    it("I-6: the overlay markup carries no inline style", () => {
        const overlay = fnSource(workbench, "renderBusyOverlay", "renderToolbar");
        assert.ok(!/style=/.test(overlay), "renderBusyOverlay must not emit style=");
    });

    it("I-7: .action-dialog and the overlay use opaque backgrounds with dark-mode pairs", () => {
        const dialog = ruleBlock(styles, ".action-dialog");
        assert.ok(!stripWs(dialog).includes("var(--panel-bg)"),
            "action-dialog must not use the translucent --panel-bg");
        assert.ok(stripWs(dialog).includes("background: rgba(255, 255, 255, 0.97); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);"),
            "action-dialog must be opaque with backdrop blur");
        const overlay = ruleBlock(styles, ".trust-reply-busy-overlay");
        assert.ok(stripWs(overlay).includes("background: rgba(255, 255, 255, 0.92);"), "overlay must be near-opaque white");
        const card = ruleBlock(styles, ".trust-reply-busy-card");
        assert.ok(stripWs(card).includes("background: rgba(255, 255, 255, 0.98);"), "card must be near-opaque white");
        const dark = styles.slice(styles.indexOf("@media (prefers-color-scheme: dark)"));
        for (const selector of [".trust-reply-busy-overlay", ".trust-reply-busy-card", ".action-dialog"]) {
            assert.ok(dark.includes(`${selector} {`), `dark media block must override ${selector}`);
        }
        assert.ok(dark.includes("background: rgba(13, 20, 32, 0.92);"), "dark overlay override");
        assert.ok(dark.includes("background: rgba(21, 31, 48, 0.98);")
            && dark.includes("border-color: rgba(148, 163, 184, 0.22);"), "dark card override");
        assert.ok(dark.includes("background: rgba(21, 31, 48, 0.97);"), "dark action-dialog override");
    });

    it("I-8: the cache-key triad is exactly 20260821-v11-reply-subject-prefill, three sites, one value", () => {
        const keys = (html.match(/\?v=[^"]+/g) || []).map((k) => k.slice(3));
        assert.strictEqual(keys.length, 3, "index.html must carry exactly three cache-busted asset URLs");
        assert.strictEqual(new Set(keys).size, 1, "all three keys must share one value");
        assert.strictEqual(keys[0], CACHE_KEY, "the shared key must be " + CACHE_KEY);
    });

    it("S-1: .trust-reply-workbench .reply-workflow-content block matches the contract verbatim", () => {
        assert.strictEqual((styles.match(/\.trust-reply-workbench \.reply-workflow-content/g) || []).length, 1,
            "the selector must stay unique in styles.css");
        const rule = ruleBlock(styles, ".trust-reply-workbench .reply-workflow-content");
        assert.strictEqual(stripWs(rule),
            ".trust-reply-workbench .reply-workflow-content { position: relative; display: flex; flex-direction: column; gap: 12px; padding: 14px; }");
    });

    it("S-2: the four overlay rules are verbatim and no spinner/keyframe is duplicated", () => {
        const overlay = ruleBlock(styles, ".trust-reply-busy-overlay");
        assert.strictEqual(stripWs(overlay),
            ".trust-reply-busy-overlay { position: absolute; inset: 0; z-index: 6; display: flex; align-items: flex-start; justify-content: center; border-radius: var(--radius-md); background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); }");
        const card = ruleBlock(styles, ".trust-reply-busy-card");
        assert.strictEqual(stripWs(card),
            ".trust-reply-busy-card { position: sticky; top: 96px; display: flex; flex-direction: column; align-items: center; gap: 10px; max-width: 420px; margin: 48px 16px; padding: 18px 22px; border: 1px solid var(--panel-border); border-radius: var(--radius-md); background: rgba(255, 255, 255, 0.98); box-shadow: var(--shadow-lg); text-align: center; }");
        const text = ruleBlock(styles, ".trust-reply-busy-text");
        assert.strictEqual(stripWs(text),
            ".trust-reply-busy-text { color: var(--text-main); font-size: 13px; font-weight: 600; line-height: 1.6; }");
        const hint = ruleBlock(styles, ".trust-reply-busy-hint");
        assert.strictEqual(stripWs(hint),
            ".trust-reply-busy-hint { color: var(--text-secondary); font-size: 12px; font-weight: 500; line-height: 1.6; }");
        assert.strictEqual((styles.match(/ai-reply-loading-spinner/g) || []).length, 1,
            "the spinner style must be reused, not duplicated");
        assert.strictEqual((styles.match(/@keyframes ai-reply-spin/g) || []).length, 1,
            "the keyframes must stay unique");
        const loadingOverlay = ruleBlock(styles, ".ai-reply-loading-overlay");
        assert.ok(stripWs(loadingOverlay).includes("background: rgba(255, 255, 255, 0.84);")
            && stripWs(loadingOverlay).includes("backdrop-filter: blur(2px);"),
            "the mailbox .ai-reply-loading-overlay must stay untouched");
    });

    it("S-3: dialog body paragraphs get scoped contrast overrides; global p untouched", () => {
        const bodyP = ruleBlock(styles, ".action-dialog-body p");
        assert.strictEqual(stripWs(bodyP),
            ".action-dialog-body p { color: var(--text-main); font-size: 13px; line-height: 1.6; }");
        const coverage = ruleBlock(styles, ".action-dialog-body .ai-reply-coverage");
        assert.strictEqual(stripWs(coverage),
            ".action-dialog-body .ai-reply-coverage { color: var(--text-secondary); }");
        const globalP = styles.match(/^p\s*\{[^}]*\}/m)[0];
        assert.strictEqual(stripWs(globalP), "p { font-size: 12px; color: var(--text-muted); margin-top: 2px; }",
            "the global p rule must stay verbatim");
        const backdrop = ruleBlock(styles, ".action-dialog::backdrop");
        assert.ok(stripWs(backdrop).includes("rgba(15, 23, 42, 0.5)") && stripWs(backdrop).includes("blur(6px)"),
            ".action-dialog::backdrop must stay untouched");
        const h3 = ruleBlock(styles, ".action-dialog h3");
        assert.ok(stripWs(h3).includes("font-size: 15px;"), ".action-dialog h3 must stay untouched");
    });
});

describe("P2 overlay + dialog contrast (rendered behavior)", () => {
    it("idle workbench renders no overlay and no aria-busy", async () => {
        const { window, document } = createSandbox(() => Promise.resolve({
            ok: true,
            status: 200,
            json: async () => bootstrapPayload()
        }));
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        assert.ok(!host.innerHTML.includes("trust-reply-busy-overlay"), "idle state must have no overlay");
        assert.ok(!host.innerHTML.includes('aria-busy="true"'), "idle state must have no aria-busy");
    });

    it("generation pending renders the overlay with cancel button, aria-busy and no inline style", async () => {
        const { window, document } = createSandbox((url) => {
            if (String(url).endsWith("/generations/stream")) {
                return Promise.resolve({ ok: true, status: 200, body: neverResolvingStream() });
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => bootstrapPayload() });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        host.dispatchEvent("click", event(actionButton("auto-run")));
        await settle();

        assert.ok(host.innerHTML.includes('class="trust-reply-busy-overlay"'), "overlay must render while generating");
        assert.ok(host.innerHTML.includes('class="trust-reply-busy-card"'), "overlay card must render");
        assert.ok(host.innerHTML.includes('data-action="cancel-generation"'), "the toolbar cancel button must stay rendered");
        assert.ok(host.innerHTML.includes('<div class="reply-workflow-content" aria-busy="true">'),
            "aria-busy must sit on the .reply-workflow-content container");
        // I-5: once the per-item request is pending, the per-request branch
        // wins (mirrors factActionBlockReason's requestPending priority).
        assert.ok(host.innerHTML.includes("本条摘要正在生成…"), "overlay text must reflect the per-item branch");
        assert.ok(host.innerHTML.includes("完成后可继续调整该条目。"), "overlay hint must reflect the per-item branch");
        assert.ok(host.innerHTML.includes("正在生成有据回答"), "the status bar must still describe the sequence");
        assert.ok(!/style=/.test(overlayFragment(host.innerHTML)),
            "the overlay fragment must not contain inline style (I-6)");
    });

    it("assembly pending renders the cancellable overlay card (I-4 runtime)", async () => {
        const { window, document } = createSandbox((url) => {
            if (String(url).endsWith("/assemble")) {
                // Never settles: assemble() stays in the ASSEMBLING pending state.
                return new Promise(() => {});
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => lockedBootstrapPayload() });
        });
        const host = new FakeElement(document);
        window.TrustReplyWorkbench.mount(host, {
            mode: "SIMULATION",
            source: { sourceType: "TRAINING_MAIL", sourceId: 101 },
            contextPath: "",
            onComplete: async () => {}
        });
        await settle();
        host.dispatchEvent("click", event(actionButton("assemble")));
        await settle();

        assert.ok(host.innerHTML.includes('class="trust-reply-busy-overlay"'), "overlay must render while assembling");
        const cardIdx = host.innerHTML.indexOf('class="trust-reply-busy-card"');
        assert.ok(cardIdx >= 0, "overlay card must render");
        const card = host.innerHTML.slice(cardIdx, host.innerHTML.indexOf("</div></div>", cardIdx) + "</div></div>".length);
        assert.ok(card.includes('data-action="cancel-generation"'), "the overlay card itself must carry the cancel button");
        assert.ok(card.includes("取消生成"), "the cancel button label must render inside the card");
        assert.ok(host.innerHTML.includes("正在请求服务端整合…"), "overlay text must describe the assembly");
        assert.ok(host.innerHTML.includes("生成期间不能改动事实与处理方式。"), "overlay hint must match the contract");
        assert.ok(host.innerHTML.includes('<div class="reply-workflow-content" aria-busy="true">'),
            "aria-busy must sit on the .reply-workflow-content container");
        assert.ok(!/style=/.test(overlayFragment(host.innerHTML)),
            "the overlay fragment must not contain inline style (I-6)");
    });
});
