"use strict";

// 03-ui 契约测试（I-1/I-2/I-3、S-1/S-2、MAIN M-5）：
// 1) 带 02 诊断字段的响应按 S-1 骨架显示 10/4/6/3，原因命中可重叠（绝不求和冒充排除人数），
//    样本限量与截断、partial、统计时间显式呈现，动态内容全部转义；
// 2) 缺新字段的旧响应只显示现有数量并注明「详细解释不可用」，禁止补 0；
// 3) HTTP/API 失败显示「预估不可用」且不弹窗；陈旧响应被 seq 丢弃；
//    诊断只读（只打预估接口，不发发送/验证请求）；
// 4) MAIN M-5：真实 index.html 必须声明两个容器 ID，且 16 个版本化资源统一到新 cache key。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const indexSource = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

function element(value = "") {
    return {
        value,
        textContent: "",
        innerHTML: "",
        hidden: true,
        checked: false,
        dataset: {},
        setAttribute() {},
        querySelectorAll() { return []; }
    };
}

const PREVIEW_HELPERS = ["baseHintHtml", "escapeHtml", "recipientPreviewHintId", "gateToggleId",
    "setRecipientPreviewState", "resetRecipientPreview", "scheduleRecipientPreview", "refreshRecipientPreview"];

/** 只加载 03 渲染所需的 app.js 函数 + DOM stub；api 由用例注入。 */
function previewSandbox(options = {}) {
    const hintId = options.kind === "editor" ? "batchConfigEditorRecipientHint" : "batchManualRecipientHint";
    const hint = element();
    const gate = element("");
    gate.checked = options.gateChecked === true;
    const snapshot = options.snapshot || {};
    const sandbox = Object.assign({
        recipientPreviewRequestSeq: { editor: 0, manual: 0 },
        recipientPreviewTimers: { editor: null, manual: null },
        clearTimeout,
        batchGateState: {
            editor: { available: options.gateAvailable !== false },
            manual: { available: options.gateAvailable !== false }
        },
        recipientPreviewHintId: () => hintId,
        gateToggleId: () => "batchGateFilter",
        buildConfigEditorRecipientSnapshot: () => snapshot,
        buildManualExecutionSnapshot: () => snapshot,
        document: { getElementById: (id) => id === hintId ? hint : (id === "batchGateFilter" ? gate : null) },
        api: async () => { throw new Error("api must be stubbed by the test"); },
        console: { warn: () => {} }
    }, options.extra || {});
    vm.createContext(sandbox);
    PREVIEW_HELPERS.forEach((name) => {
        if (!Object.prototype.hasOwnProperty.call(options.extra || {}, name)) {
            vm.runInContext(extractFn(name), sandbox);
        }
    });
    return { sandbox, hint, gate };
}

/** MAIN G-2 共同 fixture：10 母集 / 4 排除 / 6 符合 / 3 待验证，原因命中 2+2+1+1=6 次。 */
function diagnosticsResponse(overrides = {}) {
    return Object.assign({
        pending: 6,
        retryable: 0,
        totalSendable: 6,
        baseline: 10,
        excludedUnique: 4,
        eligibleBeforeLiveVerification: 6,
        awaitingVerification: 3,
        verificationPhase: "AWAITING_VERIFICATION",
        partial: false,
        asOf: "2026-10-14T09:30:00",
        reasonHitsDetail: [
            { reasonKey: "REGION", count: 2, samples: [
                { docId: "doc-r1", orcidId: "0000-0000-0000-0001" },
                { docId: "doc-r2", orcidId: "0000-0000-0000-0002" }
            ], truncated: false },
            { reasonKey: "OPERATOR_STATUS", count: 2, samples: [
                { docId: "doc-s1", orcidId: "0000-0000-0000-0003" },
                { docId: "doc-s2", orcidId: "0000-0000-0000-0004" }
            ], truncated: false },
            { reasonKey: "TEMPLATE_MISSING", count: 1, samples: [
                { docId: "doc-t1", orcidId: "0000-0000-0000-0005" }
            ], truncated: false },
            { reasonKey: "EMAIL_VERIFICATION_REJECTED", count: 1, samples: [
                { docId: "doc-e1", orcidId: "0000-0000-0000-0006" }
            ], truncated: false }
        ]
    }, overrides);
}

describe("recipient preview diagnostics (03 / I-1 / I-3 / S-1)", () => {
    it("renders the shared fixture as 10/4/6/3 with overlapping reason hits that are never summed", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
        sandbox.api = async () => diagnosticsResponse();
        sandbox.refreshRecipientPreview("editor");
        await flush();

        assert.ok(hint.innerHTML.includes("统计时间：<span>2026-10-14 09:30:00</span>"),
            "asOf must render, formatted and labelled");
        assert.ok(hint.innerHTML.includes("范围内 <strong>10</strong> 人"), "baseline must render");
        assert.ok(hint.innerHTML.includes("排除 <span class=\"batch-gate-excluded\">4</span> 人"),
            "excludedUnique must render as the exclusion count");
        assert.ok(hint.innerHTML.includes("符合任务条件 <strong>6</strong> 人"),
            "eligibleBeforeLiveVerification must render");
        assert.ok(hint.innerHTML.includes("待执行验证 <strong>3</strong> 人"),
            "awaitingVerification must render");
        assert.ok(hint.innerHTML.includes("原因人数可重叠"), "the overlap warning must render");
        assert.ok(hint.innerHTML.includes("查看排除原因与样本"), "the reason samples must be expandable");

        assert.ok(hint.innerHTML.includes("原因：REGION <strong>2</strong> 人；仅显示前 <strong>2</strong> 条"),
            "each reason hit keeps its own count and a bounded sample size");
        assert.ok(hint.innerHTML.includes("原因：TEMPLATE_MISSING <strong>1</strong> 人"),
            "every reason keeps its own line");
        assert.ok(hint.innerHTML.includes("样本：doc-t1（0000-0000-0000-0005）"),
            "samples carry the stable expert identifiers");
        assert.ok(!hint.innerHTML.includes("排除 <span class=\"batch-gate-excluded\">6</span>"),
            "reason hits (6) must never be summed into the exclusion count (4)");
        assert.ok(!hint.innerHTML.includes("范围内 <strong>4</strong>"),
            "baseline must not drift to the exclusion count");
    });

    it("marks truncated samples and partial statistics explicitly", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "manual", gateAvailable: false });
        sandbox.api = async () => diagnosticsResponse({
            partial: true,
            reasonHitsDetail: [
                { reasonKey: "REGION", count: 1201, samples: [
                    { docId: "doc-1", orcidId: "0000-0000-0000-0001" }
                ], truncated: true }
            ]
        });
        sandbox.refreshRecipientPreview("manual");
        await flush();

        assert.ok(hint.innerHTML.includes("原因：REGION <strong>1201</strong> 人；仅显示前 <strong>1</strong> 条（样本已截断）"),
            "the full count is kept while the sample list stays bounded and truncated");
        assert.ok(hint.innerHTML.includes("统计未完成"), "partial statistics must say 统计未完成");
    });

    it("renders when no reason is hit without inventing hits", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
        sandbox.api = async () => diagnosticsResponse({ reasonHitsDetail: [] });
        sandbox.refreshRecipientPreview("editor");
        await flush();

        assert.ok(hint.innerHTML.includes("原因：0 人；仅显示前 0 条"), "an empty reason list stays explicit");
        assert.ok(!hint.innerHTML.includes("样本："), "no sample line may be invented");
    });

    it("escapes reason keys and expert identifiers", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "manual", gateAvailable: false });
        sandbox.api = async () => diagnosticsResponse({
            reasonHitsDetail: [
                { reasonKey: "<img src=x onerror=alert(1)>", count: 1, samples: [
                    { docId: "\"><script>alert(1)</script>", orcidId: "'\"><b>" }
                ], truncated: false }
            ]
        });
        sandbox.refreshRecipientPreview("manual");
        await flush();

        assert.ok(!hint.innerHTML.includes("<img"), "reason key markup must be escaped");
        assert.ok(!hint.innerHTML.includes("<script"), "sample identifiers must be escaped");
        assert.ok(hint.innerHTML.includes("&lt;img src=x onerror=alert(1)&gt;"), "escaped reason key must be visible");
    });

    it("keeps the two forms on one shared skeleton", async () => {
        const texts = [];
        for (const kind of ["editor", "manual"]) {
            const { sandbox, hint } = previewSandbox({ kind, gateAvailable: false });
            sandbox.api = async () => diagnosticsResponse();
            sandbox.refreshRecipientPreview(kind);
            await flush();
            texts.push(hint.innerHTML);
        }
        assert.strictEqual(texts[0], texts[1], "both forms must render the identical S-1 skeleton");
        assert.ok(texts[0].includes("范围内 <strong>10</strong> 人"), "the shared skeleton carries the fixture numbers");
    });

    it("says 详细解释不可用 for legacy responses without padding zeros", async () => {
        const { sandbox, hint } = previewSandbox({
            kind: "editor",
            gateAvailable: false,
            snapshot: { excludeVerifiedUnavailableEmails: false }
        });
        sandbox.api = async () => ({ pending: 5, retryable: 1, totalSendable: 6 });
        sandbox.refreshRecipientPreview("editor");
        await flush();

        assert.ok(hint.innerHTML.includes("详细解释不可用"), "a legacy response must name the missing explanation");
        assert.ok(hint.innerHTML.includes("当前条件命中 <strong>6</strong>"), "existing counts must still render");
        assert.ok(!hint.innerHTML.includes("范围内"), "no diagnostics skeleton may be faked for legacy responses");
        assert.ok(!hint.innerHTML.includes("排除 <span"), "no exclusion count may be invented");
    });

    it("renders invalidated diagnostics on an HTTP/API failure without popups", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
        const failure = new Error("500 Internal Server Error");
        failure.status = 500;
        sandbox.api = async () => { throw failure; };
        sandbox.refreshRecipientPreview("editor");
        await flush();

        assert.strictEqual(hint.textContent, "预估不可用", "an API failure renders 预估不可用");
        assert.ok(!hint.innerHTML.includes("batch-gate-warnline") && !hint.innerHTML.includes("batch-gate-excluded"),
            "a failed preview must not keep a stale exclusion line");
    });

    it("keeps the concrete message for non-HTTP failures (no fake diagnostics)", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "manual", gateAvailable: false });
        sandbox.api = async () => { throw new Error("roundSize must be a number"); };
        sandbox.refreshRecipientPreview("manual");
        await flush();

        assert.strictEqual(hint.textContent, "预估失败：roundSize must be a number",
            "client-side errors keep their concrete message");
    });

    it("discards a stale response and never writes it over the latest selection", async () => {
        const { sandbox, hint } = previewSandbox({ kind: "editor", gateAvailable: false });
        const pending = [];
        sandbox.api = () => new Promise((resolve, reject) => { pending.push({ resolve, reject }); });

        sandbox.refreshRecipientPreview("editor");   // seq 1
        sandbox.refreshRecipientPreview("editor");   // seq 2
        assert.strictEqual(pending.length, 2, "each preview issues exactly one request when the gate is unavailable");

        pending[1].resolve(diagnosticsResponse({ baseline: 11, excludedUnique: 5,
            eligibleBeforeLiveVerification: 6, awaitingVerification: 4 }));
        await flush();
        assert.ok(hint.innerHTML.includes("范围内 <strong>11</strong> 人"), "the fresh round must render");

        pending[0].resolve(diagnosticsResponse());   // stale: baseline 10
        await flush();
        assert.ok(hint.innerHTML.includes("范围内 <strong>11</strong> 人"),
            "a stale response must not overwrite the latest render");
        assert.ok(!hint.innerHTML.includes("范围内 <strong>10</strong> 人"), "the stale numbers must stay discarded");
    });

});

describe("03 static asset contract (M-5 / S-1 / S-2)", () => {
    it("real index.html declares both recipient hint containers", () => {
        assert.ok(indexSource.includes('id="batchConfigEditorRecipientHint"'),
            "the editor form must declare batchConfigEditorRecipientHint in the real index.html");
        assert.ok(indexSource.includes('id="batchManualRecipientHint"'),
            "the manual form must declare batchManualRecipientHint in the real index.html");
        assert.strictEqual(indexSource.split('id="batchConfigEditorRecipientHint"').length - 1, 1,
            "the editor container is declared exactly once");
        assert.strictEqual(indexSource.split('id="batchManualRecipientHint"').length - 1, 1,
            "the manual container is declared exactly once");
    });

    it("the two gate hints match the rendered copy and never call gate exclusions a failure", () => {
        const gateSource = extractFn("refreshBatchGateState");
        const editorHint = /editor:\s*"([^"]+)"/.exec(gateSource)[1];
        const manualHint = /manual:\s*"([^"]+)"/.exec(gateSource)[1];
        const editorHtml = /id="batchConfigEditorGateFilterHint">([^<]*)</.exec(indexSource)[1];
        const manualHtml = /id="batchManualGateFilterHint">([^<]*)</.exec(indexSource)[1];
        assert.strictEqual(editorHtml, editorHint, "editor static copy must equal the rendered copy");
        assert.strictEqual(manualHtml, manualHint, "manual static copy must equal the rendered copy");
        assert.ok(editorHint.includes("关闭时不因个性化缺项排除"), "the closed gate must not exclude personalization gaps");
        assert.ok(!indexSource.includes("计入失败"), "no gate copy may call a template gap a failure");
    });

    it("collapses the 16 versioned references onto one new cache key", () => {
        const keyMatch = indexSource.match(/styles\.css\?v=([^"'&<>]+)/);
        assert.ok(keyMatch, "index.html must register styles.css with a ?v= cache key");
        const keys = [...indexSource.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 16, "index.html must version exactly 16 resources");
        assert.ok(keys.every((key) => key === keyMatch[1]),
            "all 16 resources must share the single new cache key, actual: " + keys.join(","));
        assert.ok(/^[0-9]{8}-[a-z0-9-]+$/.test(keyMatch[1]),
            "the unified key must stay a <yyyymmdd>-<slug> literal, got: " + keyMatch[1]);
        assert.ok(!indexSource.includes("20261013-materials-zip"), "the previous key must have zero hits");
    });
});
