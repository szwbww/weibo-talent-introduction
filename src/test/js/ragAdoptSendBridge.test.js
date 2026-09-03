"use strict";

// 03b (I-39/I-44): RAG 草稿采用 → 人工发送桥接的前端契约。
// - usedFactCodes 形态（05 的 RAG 载荷 { text, usedFactCodes, ragCorpusFingerprint,
//   unaddressed }）→ manualReplyQaContext = { ragFactCodes, ragCorpusFingerprint,
//   baselineText }，不含 qaRuleIds；不调用 schedulePreflightCheck()（I-44）。
// - canonicalFactIds 形态（旧可信工作台）→ 行为与改动前一致：qaRuleIds 上下文 +
//   buildTrustReplyAssemblySnapshot + schedulePreflightCheck()。
// - 发送请求组装（source 断言）：RAG 形态携带 ragFactCodes + ragCorpusFingerprint，
//   旧 qaRuleIds 分支保持原位。

const { describe, it } = require("node:test");
const fs = require("fs");
const vm = require("vm");
const assert = require("assert");

const appJsPath = "src/main/resources/static/app.js";
const app = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = app.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function makeEditor() {
    return {
        innerText: "",
        innerHTML: "",
        dataset: {},
        closest: () => ({ scrollIntoView: () => {} })
    };
}

function makeSandbox() {
    const editor = makeEditor();
    const ctx = {
        editor,
        aiReplyState: { adoptContext: null },
        manualReplyQaContext: null,
        calls: { schedulePreflight: 0, resetPreflight: 0, snapshot: 0 },
        showStatus: () => {},
        $: (sel) => (sel === "#manualRichReplyEditor" ? editor : null),
        schedulePreflightCheck: () => {
            ctx.calls.schedulePreflight += 1;
        },
        resetPreflightState: () => {
            ctx.calls.resetPreflight += 1;
        },
        buildTrustReplyAssemblySnapshot: (assembly) => {
            ctx.calls.snapshot += 1;
            return {
                snapshotBuilt: true,
                hasFrame: !!assembly.frameSnapshot,
                hasItems: Array.isArray(assembly.itemVersions)
            };
        }
    };
    vm.createContext(ctx);
    vm.runInContext(extractFn("adoptTrustReplyAssembly"), ctx);
    return ctx;
}

// vm 上下文里的数组/对象属于另一个 realm（原型不同），deepStrictEqual 前先拷回宿主 realm。
function hostArray(value) {
    return Array.from(value);
}

describe("rag adopt -> send bridge (03b T5)", () => {
    it("I-44: usedFactCodes shape builds ragFactCodes context, carries fingerprint, and never schedules preflight", () => {
        const ctx = makeSandbox();
        const ragAssembly = {
            text: "Dear Prof,\n\nOur programme funds up to 1.2M EUR.",
            usedFactCodes: ["KB-FUND-033", "KB-COMP-007", "KB-PROG-002"],
            ragCorpusFingerprint: "e62421a42c432cf3",
            unaddressed: []
        };
        ctx.adoptTrustReplyAssembly(42, ragAssembly);

        assert.strictEqual(ctx.editor.innerText, ragAssembly.text, "adopt must fill the editor with the RAG body");
        const context = ctx.manualReplyQaContext;
        assert.ok(context, "manualReplyQaContext must be set");
        assert.deepStrictEqual(hostArray(context.ragFactCodes), ragAssembly.usedFactCodes,
            "ragFactCodes must mirror usedFactCodes in order");
        assert.strictEqual(context.ragCorpusFingerprint, "e62421a42c432cf3",
            "ragCorpusFingerprint must be carried into the send context");
        assert.strictEqual(context.baselineText, ragAssembly.text, "baselineText must be the adopted body");
        assert.ok(!("qaRuleIds" in context), "RAG context must not contain qaRuleIds");
        assert.strictEqual(ctx.aiReplyState.adoptContext.trustReplyAssembly, null,
            "RAG adopt must not carry a truthy trustReplyAssembly snapshot");
        assert.deepStrictEqual(hostArray(ctx.aiReplyState.adoptContext.qaRuleIds), [],
            "RAG adopt keeps no Long canonical ids on adoptContext");
        assert.strictEqual(ctx.calls.schedulePreflight, 0,
            "I-44: schedulePreflightCheck must not be called on RAG shape");
        assert.strictEqual(ctx.calls.snapshot, 0, "RAG shape must not build a legacy assembly snapshot");
        assert.strictEqual(ctx.calls.resetPreflight, 1,
            "RAG shape must clear any pending legacy preflight timer/panel");
    });

    it("I-44: empty usedFactCodes still selects the RAG shape (evidence-free draft keeps the fingerprint gate)", () => {
        const ctx = makeSandbox();
        ctx.adoptTrustReplyAssembly(42, {
            text: "No facts were needed.",
            usedFactCodes: [],
            ragCorpusFingerprint: "e62421a42c432cf3"
        });
        const context = ctx.manualReplyQaContext;
        assert.deepStrictEqual(hostArray(context.ragFactCodes), [],
            "ragFactCodes must be [] for an evidence-free RAG draft");
        assert.strictEqual(context.ragCorpusFingerprint, "e62421a42c432cf3");
        assert.ok(!("qaRuleIds" in context));
        assert.strictEqual(ctx.calls.schedulePreflight, 0,
            "I-44: empty fact list must not schedule preflight either");
    });

    it("canonicalFactIds shape behaves exactly as before (qaRuleIds context + snapshot + preflight)", () => {
        const ctx = makeSandbox();
        const legacyAssembly = {
            renderedDraftText: "Legacy rendered body",
            rawDraftText: "<p>Legacy rendered body</p>",
            canonicalFactIds: [7, 9, 10],
            evidenceSetVersion: "letter-e1",
            draftHash: "legacy-hash",
            itemVersions: [{ handling: "ANSWER_WITH_EVIDENCE" }],
            frameSnapshot: { selection: { salutationSnippetId: 1 }, version: "frame-v1" }
        };
        ctx.adoptTrustReplyAssembly(7, legacyAssembly);

        assert.strictEqual(ctx.editor.innerText, "Legacy rendered body");
        const context = ctx.manualReplyQaContext;
        assert.deepStrictEqual(hostArray(context.qaRuleIds), [7, 9, 10],
            "legacy shape keeps qaRuleIds context");
        assert.ok(!("ragFactCodes" in context), "legacy context must not contain ragFactCodes");
        assert.strictEqual(context.baselineText, "Legacy rendered body");
        assert.strictEqual(ctx.aiReplyState.adoptContext.trustReplyAssembly.snapshotBuilt, true,
            "legacy shape still builds the trust reply assembly snapshot");
        assert.strictEqual(ctx.calls.snapshot, 1);
        assert.strictEqual(ctx.calls.schedulePreflight, 1,
            "legacy shape still schedules the preflight check");
        assert.strictEqual(ctx.calls.resetPreflight, 0);
    });

    it("send request assembly carries ragFactCodes+ragCorpusFingerprint on RAG shape and keeps the qaRuleIds branch verbatim", () => {
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        assert.ok(sendIdx > 0, "send-manual-rich-reply handler must exist");
        const sendBlock = app.slice(sendIdx, sendIdx + 2600);
        assert.ok(sendBlock.includes("requestBody.ragFactCodes = manualReplyQaContext.ragFactCodes;"),
            "RAG send must attach ragFactCodes");
        assert.ok(sendBlock.includes("requestBody.ragCorpusFingerprint = manualReplyQaContext.ragCorpusFingerprint;"),
            "RAG send must attach ragCorpusFingerprint");
        assert.ok(sendBlock.includes("if (manualReplyQaContext?.qaRuleIds?.length)"),
            "legacy qaRuleIds branch must remain in the send handler");
        assert.ok(sendBlock.includes("requestBody.qaRuleIds = manualReplyQaContext.qaRuleIds;"),
            "legacy qaRuleIds attachment must be untouched");
        // 正文被运营改动时 RAG 分支同样标记 edited（与旧分支同一判据）。
        const ragBranch = sendBlock.slice(0, sendBlock.indexOf("if (manualReplyQaContext?.qaRuleIds?.length)"));
        assert.ok(ragBranch.includes("requestBody.edited = editor.innerText.trim() !== (manualReplyQaContext.baselineText || \"\").trim();"),
            "RAG branch must keep the edited flag on baseline drift");
        assert.ok(!ragBranch.includes("requestBody.edited = manualReplyQaContext.edited"),
            "RAG branch must compute edited from the live editor, not the context");
    });

    // G-8: 这两个 id 由 app.js 动态渲染（不在 index.html 静态源里）——断言生成它们的
    // 模板仍在 app.js 源中，杜绝「测试 stub 掩盖悬空引用」。
    it("G-8: the ids adopt and RAG preflight-skip touch are still produced by app.js templates", () => {
        assert.ok(/<div id="manualRichReplyEditor"/.test(app),
            "manualRichReplyEditor render template must remain in app.js");
        assert.ok(/<div id="manualReplyPreflight"/.test(app),
            "manualReplyPreflight render template must remain in app.js");
    });
});
