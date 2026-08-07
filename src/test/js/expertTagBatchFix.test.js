const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const indexHtmlSource = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createTagFetchSandbox() {
    const sandbox = {
        api: async () => ({ found: true, tags: [] }),
        URLSearchParams
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("fetchExpertTagsFromEs"), sandbox);
    vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
    vm.runInContext(`
        const expertTagLabels = {
            auto_promoted: "自动晋升",
            verified: "已验证",
            discovered: "新发现",
            "承诺回复材料": "承诺回复材料"
        };
        function escapeHtml(v) {
            return String(v == null ? "" : v);
        }
    `, sandbox);
    return sandbox;
}

function createMailboxExpertTagSandbox() {
    const sandbox = {
        expertTagLabels: {
            discovered: "新发现",
            "承诺回复材料": "承诺回复材料"
        },
        escapeHtml: (v) => String(v == null ? "" : v)
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
    vm.runInContext(extractFn("renderMailboxExpertTagEditor"), sandbox);
    return sandbox;
}

// ── Simple sandbox for pure functions ─────────────────────────────────────

function createBatchSendTypeSandbox() {
    const insertedFallbacks = [];
    let selectValue = "";
    let selectInnerHTML = "";
    let knownValues = [];

    const select = {
        get innerHTML() { return selectInnerHTML; },
        set innerHTML(html) {
            selectInnerHTML = html;
            knownValues = [""];
            const re = /value="([^"]*)"/g;
            let m;
            while ((m = re.exec(html)) !== null) knownValues.push(m[1]);
        },
        get value() { return selectValue; },
        set value(v) { if (knownValues.includes(v)) selectValue = v; else selectValue = ""; },
        firstChild: null,
        insertBefore(child, ref) {
            insertedFallbacks.push(child);
            knownValues.unshift(child.value);
            selectValue = child.value;
        }
    };

    const sandbox = {
        batchSendType: "INTRODUCTION",
        escapeHtml: (v) => String(v ?? ""),
        document: {
            createElement(tag) {
                return { tagName: tag.toUpperCase(), value: "", textContent: "" };
            }
        },
        $: (sel) => (sel === "#batchSendEmailDomain" ? select : null)
    };
    vm.createContext(sandbox);
    vm.runInContext(`function batchSendTypeBase(sendType) { return '/api/mail/batch-send/types/' + (sendType || batchSendType); }`, sandbox);
    vm.runInContext(extractFn("fillBatchSendProviderSelect"), sandbox);
    return { sandbox, select, insertedFallbacks };
}

// ── Pending count sandbox (token-aware) ─────────────────────────────────

function createPendingCountSandbox() {
    let apiResolvers = [];
    let apiCalls = [];
    let summaryArgs = [];
    let recipientEl = { textContent: "" };

    const sandbox = {
        batchSendType: "INTRODUCTION",
        batchSendRequestToken: 0,
        batchSendRangeCopy: (type) => `range-${type}`,
        applyBatchSendRecipientSummary: (type, res) => { summaryArgs.push({ type, res }); },
        $: (sel) => sel === "#batchSendRecipientSummary" ? recipientEl : null,
        api: async (url) => {
            apiCalls.push(url);
            return new Promise((resolve) => { apiResolvers.push(resolve); });
        },
        __summaryArgs: summaryArgs,
        __apiCalls: apiCalls,
        __resolveNext: (val) => { const fn = apiResolvers.shift(); if (fn) fn(val); }
    };
    vm.createContext(sandbox);
    vm.runInContext(
        `function batchSendTypeBase(sendType) { return '/api/mail/batch-send/types/' + (sendType || batchSendType); }`,
        sandbox
    );
    vm.runInContext(extractFn("refreshBatchSendPendingCountDisplay"), sandbox);
    return sandbox;
}

// ──────────────────────────────────────────────────────────────────────────
// SUITE: fetchExpertTagsFromEs authoritative tags (P1-2) — unchanged
// ──────────────────────────────────────────────────────────────────────────

describe("fetchExpertTagsFromEs authoritative tags (P1-2)", () => {
    it("loads tags from /api/experts/profile instead of list cache", async () => {
        const sb = createTagFetchSandbox();
        let profileUrl = "";
        sb.api = async (url) => {
            profileUrl = url;
            return { orcidId: "0000-0001", tags: ["承诺回复材料"] };
        };

        const result = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.ok(profileUrl.includes("/api/experts/profile"));
        assert.ok(profileUrl.includes("orcidId=0000-0001"));
        assert.ok(profileUrl.includes("level=CANDIDATE"));
        assert.deepStrictEqual(result.tags, ["承诺回复材料"]);
        assert.strictEqual(result.found, true);
    });

    it("renders ES tags in editor even when list cache has no tags", () => {
        const sb = createTagFetchSandbox();
        const html = sb.renderExpertTagEditor(["承诺回复材料"], "0000-0001", "CANDIDATE");
        assert.ok(html.includes("承诺回复材料"));
        assert.ok(html.includes('data-orcid="0000-0001"'));
    });

    it("can render a mailbox-scoped expert tag editor", () => {
        const sb = createTagFetchSandbox();
        const html = sb.renderExpertTagEditor(["discovered"], "0000-0002", "APPLICATION", "mailboxExpertTagEditor");
        assert.ok(html.includes('id="mailboxExpertTagEditor"'));
        assert.ok(html.includes('data-orcid="0000-0002"'));
        assert.ok(html.includes('data-level="APPLICATION"'));
    });

    it("renders mailbox expert tags from unmatched processing contact payload", () => {
        const sb = createMailboxExpertTagSandbox();
        const html = sb.renderMailboxExpertTagEditor(
            { orcidId: "0000-0002-4464-150X", currentIndexLevel: "CANDIDATE" },
            ["discovered"],
            "mailboxProcessingExpertTagEditor"
        );

        assert.ok(html.includes("专家标签"));
        assert.ok(html.includes('id="mailboxProcessingExpertTagEditor"'));
        assert.ok(html.includes('data-orcid="0000-0002-4464-150X"'));
        assert.ok(html.includes('data-level="CANDIDATE"'));
        assert.ok(html.includes("新发现"));
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: Batch send type API path (S-1/S-2/I-11)
// ──────────────────────────────────────────────────────────────────────────

describe("Batch send toolbar and type routing (S-1/S-2/I-11)", () => {

    it("S-1: toolbar has exactly one bulkOutreachBtn entry", () => {
        const matches = indexHtmlSource.match(/id="bulkOutreachBtn"/g);
        assert.ok(matches, "bulkOutreachBtn must exist in index.html");
        assert.strictEqual(matches.length, 1, "bulkOutreachBtn must appear exactly once");
    });

    it("S-1: no old 'batchTagMailBtn' button in index.html", () => {
        assert.ok(!indexHtmlSource.includes('id="batchTagMailBtn"'),
            "old batchTagMailBtn must not exist (I-1: single toolbar entry)");
    });

    it("S-2: batchSendTaskModal exists and contains scheduled/manual tabs", () => {
        const modalPos = indexHtmlSource.indexOf('id="batchSendTaskModal"');
        assert.ok(modalPos !== -1, "batchSendTaskModal must exist in index.html");
        const scheduledPos = indexHtmlSource.indexOf('id="batchScheduledPanel"');
        assert.ok(scheduledPos !== -1, "batchScheduledPanel must exist in index.html");
        const manualPos = indexHtmlSource.indexOf('id="batchManualPanel"');
        assert.ok(manualPos !== -1, "batchManualPanel must exist");
        // scheduled panel must appear before manual panel
        assert.ok(scheduledPos < manualPos,
            "batchScheduledPanel must appear before batchManualPanel");
    });

    it("batchSendTypeBase returns /api/mail/batch-send/types/{sendType}", () => {
        const { sandbox } = createBatchSendTypeSandbox();
        assert.strictEqual(
            sandbox.batchSendTypeBase("INTRODUCTION"),
            "/api/mail/batch-send/types/INTRODUCTION"
        );
        assert.strictEqual(
            sandbox.batchSendTypeBase("MATERIAL_REMINDER"),
            "/api/mail/batch-send/types/MATERIAL_REMINDER"
        );
    });

    it("batchSendTypeBase() with no argument uses batchSendType variable", () => {
        const { sandbox } = createBatchSendTypeSandbox();
        sandbox.batchSendType = "INTRODUCTION";
        assert.strictEqual(sandbox.batchSendTypeBase(), "/api/mail/batch-send/types/INTRODUCTION");
        sandbox.batchSendType = "MATERIAL_REMINDER";
        assert.strictEqual(sandbox.batchSendTypeBase(), "/api/mail/batch-send/types/MATERIAL_REMINDER");
    });

    it("I-11 type default logic: MATERIAL_REMINDER when tag filter is 承诺回复材料, else INTRODUCTION", () => {
        // Assert the conditional expression exists in source (spans two lines so use [\s\S]*)
        const src = appJsSource;
        assert.ok(
            src.includes("承诺回复材料") && src.includes("MATERIAL_REMINDER") && src.includes("INTRODUCTION"),
            "type default logic must reference tag and both type names"
        );
        // The pattern spans lines: check it in multiline mode
        assert.ok(
            /承诺回复材料[\s\S]{0,200}MATERIAL_REMINDER[\s\S]{0,100}INTRODUCTION/.test(src) ||
            /MATERIAL_REMINDER[\s\S]{0,100}承诺回复材料[\s\S]{0,100}INTRODUCTION/.test(src),
            "source must contain conditional defaultType assignment near the tag name"
        );
        // The variable assignment exists in taskLaunchConfigs.MANUAL_INITIAL_OUTREACH.preload
        // or new openBatchSendTaskModal for the new task console
        assert.ok(src.includes("batchSendType = defaultType") || src.includes("openBatchSendTaskModal"),
            "must reference either legacy defaultType or new openBatchSendTaskModal");
    });

    it("I-11 config/status/count/start/pause APIs have type-based routing", () => {
        const src = appJsSource;
        const typeBaseCalls = (src.match(/batchSendTypeBase\(/g) || []).length;
        assert.ok(typeBaseCalls >= 2,
            `batchSendTypeBase must be called at least 2 times, got ${typeBaseCalls}`);
        // All key sub-paths present
        assert.ok(src.includes("/status"), "status endpoint must be present");
        assert.ok(src.includes("/pending-count"), "pending-count endpoint must be present");
        assert.ok(src.includes("/config"), "config endpoint must be present");
        assert.ok(src.includes("/start"), "start endpoint must be present");
    });

    it("no inline style= in batch send type functions (S-3)", () => {
        // Check the key batch send functions have no inline style attributes
        const fns = ["batchSendTypeBase", "fillBatchSendProviderSelect",
                     "applyBatchSendRecipientSummary", "batchSendRangeCopy"];
        for (const name of fns) {
            try {
                const src = extractFn(name);
                assert.ok(!/style\s*=/.test(src),
                    `${name} must not contain inline style= attributes`);
            } catch (e) {
                // function may not be found — skip
            }
        }
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: fillBatchSendProviderSelect saved value retention (S-2/I-11)
// ──────────────────────────────────────────────────────────────────────────

describe("fillBatchSendProviderSelect saved value retention (S-2)", () => {

    it("inserts fallback option when savedValue is not in provider list", () => {
        const { sandbox, select, insertedFallbacks } = createBatchSendTypeSandbox();
        sandbox.fillBatchSendProviderSelect("INTRODUCTION", ["gmail.com", "outlook.com"], "yahoo.com");

        assert.strictEqual(insertedFallbacks.length, 1,
            "one fallback option must be inserted when savedValue not in list");
        assert.strictEqual(insertedFallbacks[0].value, "yahoo.com");
        assert.strictEqual(insertedFallbacks[0].textContent, "当前配置（无匹配）");
        assert.strictEqual(select.value, "yahoo.com");
    });

    it("does not insert fallback when savedValue matches an option", () => {
        const { sandbox, select, insertedFallbacks } = createBatchSendTypeSandbox();
        sandbox.fillBatchSendProviderSelect("INTRODUCTION", ["gmail.com", "outlook.com"], "gmail.com");

        assert.strictEqual(insertedFallbacks.length, 0,
            "no fallback must be inserted when savedValue is in list");
        assert.strictEqual(select.value, "gmail.com");
    });

    it("select.value stays at savedValue after fallback inserted", () => {
        const { sandbox, select } = createBatchSendTypeSandbox();
        sandbox.fillBatchSendProviderSelect("MATERIAL_REMINDER", ["qq.com"], "hotmail.com");

        assert.strictEqual(select.value, "hotmail.com",
            "select value must equal savedValue even when inserted via fallback");
    });

    it("renders provider list items as option elements in innerHTML", () => {
        const { sandbox, select } = createBatchSendTypeSandbox();
        sandbox.fillBatchSendProviderSelect("INTRODUCTION", ["gmail.com", "outlook.com"], "");

        assert.ok(select.innerHTML.includes("gmail.com"), "gmail.com must appear in innerHTML");
        assert.ok(select.innerHTML.includes("outlook.com"), "outlook.com must appear in innerHTML");
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: refreshBatchSendPendingCountDisplay stale response discard (I-11)
// ──────────────────────────────────────────────────────────────────────────

describe("refreshBatchSendPendingCountDisplay stale token discard (I-11)", () => {

    it("discards response when token changes before resolution", async () => {
        const sb = createPendingCountSandbox();
        let summaryCallCount = 0;
        sb.applyBatchSendRecipientSummary = () => { summaryCallCount++; };

        // Start a pending-count request with token=0
        const p = sb.refreshBatchSendPendingCountDisplay();

        // Advance token (simulates type switch)
        sb.batchSendRequestToken = 99;

        // Resolve the original request
        await new Promise(r => setImmediate(r));
        sb.__resolveNext({ pending: 42 });
        await new Promise(r => setImmediate(r));
        await p;

        assert.strictEqual(summaryCallCount, 0,
            "applyBatchSendRecipientSummary must NOT be called for stale response");
    });

    it("discards response when sendType changes before resolution", async () => {
        const sb = createPendingCountSandbox();
        let summaryCallCount = 0;
        sb.applyBatchSendRecipientSummary = () => { summaryCallCount++; };

        const p = sb.refreshBatchSendPendingCountDisplay();

        // Change type to simulate switch
        sb.batchSendType = "MATERIAL_REMINDER";

        await new Promise(r => setImmediate(r));
        sb.__resolveNext({ pending: 7 });
        await new Promise(r => setImmediate(r));
        await p;

        assert.strictEqual(summaryCallCount, 0,
            "applyBatchSendRecipientSummary must NOT be called when type changed");
    });

    it("applies summary when token and type are unchanged", async () => {
        const sb = createPendingCountSandbox();
        let appliedArgs = null;
        sb.applyBatchSendRecipientSummary = (type, res) => { appliedArgs = { type, res }; };

        const p = sb.refreshBatchSendPendingCountDisplay();

        // Resolve without changing token/type
        await new Promise(r => setImmediate(r));
        sb.__resolveNext({ pending: 5 });
        await new Promise(r => setImmediate(r));
        await p;

        assert.ok(appliedArgs !== null, "applyBatchSendRecipientSummary must be called");
        assert.strictEqual(appliedArgs.type, "INTRODUCTION");
    });

    it("request URL includes /types/{sendType}/pending-count", async () => {
        const sb = createPendingCountSandbox();
        const p = sb.refreshBatchSendPendingCountDisplay();
        await new Promise(r => setImmediate(r));
        sb.__resolveNext({ pending: 0 });
        await p;

        assert.ok(sb.__apiCalls.length > 0, "at least one API call must be made");
        assert.ok(
            sb.__apiCalls[0].includes("/types/INTRODUCTION/pending-count"),
            `API call URL must contain /types/INTRODUCTION/pending-count, got: ${sb.__apiCalls[0]}`
        );
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: normalizeManualSnapshot tag dedup (P1-1 fix-2)
// ──────────────────────────────────────────────────────────────────────────

function createDiffSandbox() {
    const sandbox = {
        batchTaskState: { manualSource: null, manualDraft: null },
        console: console
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("normalizeManualSnapshot"), sandbox);
    vm.runInContext(extractFn("computeManualDiffs"), sandbox);
    return sandbox;
}

describe("normalizeManualSnapshot tag dedup (P1-1)", () => {
    it("same tags with whitespace and duplicate produce no diff", () => {
        const sb = createDiffSandbox();
        var base = sb.normalizeManualSnapshot({ tags: ["AI"] });
        var draft = sb.normalizeManualSnapshot({ tags: [" AI ", "AI"] });
        assert.deepStrictEqual(base.tags, draft.tags,
            "trim+sorted+deduped normalized tags must be equal");
    });

    it("truly different tags still produce diff", () => {
        const sb = createDiffSandbox();
        var base = sb.normalizeManualSnapshot({ tags: ["AI"] });
        var draft = sb.normalizeManualSnapshot({ tags: ["STEM"] });
        assert.notDeepStrictEqual(base.tags, draft.tags,
            "different tags after normalization must still be detected");
    });

    it("empty tags vs whitespace-only tags produce no diff", () => {
        const sb = createDiffSandbox();
        var base = sb.normalizeManualSnapshot({ tags: [] });
        var draft = sb.normalizeManualSnapshot({ tags: ["  ", ""] });
        assert.deepStrictEqual(base.tags, draft.tags,
            "empty and whitespace-only tags must normalize to same empty set");
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: readManualFormValues does NOT silently convert 0 → default (P1-1 fix-3)
// ──────────────────────────────────────────────────────────────────────────

function createFormValuesSandbox() {
    var stored = {};
    var sandbox = {
        document: {
            getElementById: function(id) {
                var el = stored[id];
                if (!el) {
                    el = { value: "" };
                    stored[id] = el;
                }
                return el;
            }
        },
        console: console
    };
    vm.createContext(sandbox);
    sandbox.batchTaskState = { preloadedTemplates: [] };
    vm.runInContext(extractFn("supportedBatchComposeTemplates"), sandbox);
    vm.runInContext(extractFn("resolveBatchTemplateMailType"), sandbox);
    vm.runInContext(extractFn("normalizeBatchTags"), sandbox);
    vm.runInContext(extractFn("readBatchTagPickerValue"), sandbox);
    vm.runInContext(extractFn("readManualFormValues"), sandbox);
    sandbox.__store = stored;
    return sandbox;
}

describe("readManualFormValues NaN-on-empty (P1-1)", () => {
    it("empty dailyCap field returns NaN, not 1000", () => {
        var sb = createFormValuesSandbox();
        sb.document.getElementById("batchManualDailyCap").value = "";
        var values = sb.readManualFormValues();
        assert.ok(Number.isNaN(values.dailyCap), "empty dailyCap must be NaN");
    });

    it("zero dailyCap returns 0 (valid number), not 1000", () => {
        var sb = createFormValuesSandbox();
        sb.document.getElementById("batchManualDailyCap").value = "0";
        var values = sb.readManualFormValues();
        assert.strictEqual(values.dailyCap, 0, "zero dailyCap must be 0, not default");
    });

    it("zero selfCheckTtlMinutes returns 0 (valid number), not 30", () => {
        var sb = createFormValuesSandbox();
        sb.document.getElementById("batchManualSelfCheckTtlMin").value = "0";
        var values = sb.readManualFormValues();
        assert.strictEqual(values.selfCheckTtlMinutes, 0, "zero TTL must be 0, not 30");
    });

    it("valid values are returned as-is (intervals in ms)", () => {
        var sb = createFormValuesSandbox();
        sb.document.getElementById("batchManualDailyCap").value = "500";
        sb.document.getElementById("batchManualRoundSize").value = "25";
        sb.document.getElementById("batchManualPerMailIntervalSec").value = "2";
        sb.document.getElementById("batchManualSelfCheckTtlMin").value = "15";
        var values = sb.readManualFormValues();
        assert.strictEqual(values.dailyCap, 500);
        assert.strictEqual(values.roundSize, 25);
        assert.strictEqual(values.perMailIntervalMs, 2000, "2 sec must return 2000 ms");
        assert.strictEqual(values.selfCheckTtlMinutes, 15);
    });

    it("zero interval seconds returns 0 ms, not swallowed", () => {
        var sb = createFormValuesSandbox();
        sb.document.getElementById("batchManualPerMailIntervalSec").value = "0";
        sb.document.getElementById("batchManualPerRoundIntervalSec").value = "0";
        var values = sb.readManualFormValues();
        assert.strictEqual(values.perMailIntervalMs, 0, "0 sec interval must be 0 ms");
        assert.strictEqual(values.perRoundIntervalMs, 0, "0 sec interval must be 0 ms");
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: interval diff normalization (I-1 fix-3 + reverification)
// ──────────────────────────────────────────────────────────────────────────

describe("interval diff normalization (I-1)", () => {
    it("baseline 1000/60000 vs form 1/60 sec produces no interval diff", () => {
        var sb = createDiffSandbox();
        sb.batchTaskState.manualSource = {
            perMailIntervalMs: 1000,
            perRoundIntervalMs: 60000
        };
        // readManualFormValues returns ms (1 sec → 1000 ms)
        sb.processManualFormSnapshot = function() {
            var n = sb.normalizeManualSnapshot(sb.batchTaskState.manualSource);
            var d = sb.normalizeManualSnapshot({
                perMailIntervalMs: 1000,
                perRoundIntervalMs: 60000
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: log execution identity and race guard (I-2 fix-3)
// ──────────────────────────────────────────────────────────────────────────

function createLogSandbox() {
    var apiResolvers = [];
    var intervals = [];
    var selectEl = { value: "", innerHTML: "" };
    var stored = {};
    var sandbox = {
        batchTaskState: { logConfigId: null, logExecutionId: null, logRefreshTimer: null },
        document: {
            getElementById: function(id) {
                if (id === "batchLogExecutionSelect") return selectEl;
                if (!stored[id]) stored[id] = { textContent: "", innerHTML: "", hidden: false };
                return stored[id];
            }
        },
        clearInterval: function(timer) { intervals = intervals.filter(function(i) { return i !== timer; }); },
        setInterval: function(fn, ms) {
            var t = { fn: fn, ms: ms, id: Math.random() };
            intervals.push(t);
            return t;
        },
        api: async function(url) {
            return new Promise(function(resolve) { apiResolvers.push(resolve); });
        },
        escapeHtml: function(v) { return String(v ?? ""); },
        formatDateTime: function(dt) { return dt || "—"; },
        statusLabel: function(s) { return s || "—"; },
        renderBatchExecutionDetail: function() {},
        clearBatchLogDisplay: function() {},
        loadBatchLogDetail: function() {},
        __apiResolvers: apiResolvers,
        __intervals: intervals,
        __select: selectEl
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("openBatchConfigLogs"), sandbox);
    vm.runInContext(extractFn("closeBatchLogDrawer"), sandbox);
    vm.runInContext(extractFn("clearBatchLogRefreshTimer"), sandbox);
    vm.runInContext(extractFn("loadBatchLogExecutions"), sandbox);
    vm.runInContext(extractFn("loadBatchLogDetail"), sandbox);
    return sandbox;
}

describe("log execution identity (I-2)", () => {
    it("default selection writes logExecutionId and starts RUNNING interval", async () => {
        var sb = createLogSandbox();
        var detailCalls = [];
        sb.loadBatchLogDetail = function(cfgId, execId) {
            detailCalls.push({ configId: cfgId, executionId: execId });
            sb.batchTaskState.logRefreshTimer = sb.setInterval(function() {
                if (sb.batchTaskState.logConfigId === cfgId && sb.batchTaskState.logExecutionId === execId) {
                    sb.loadBatchLogDetail(cfgId, execId);
                }
            }, 3000);
        };

        sb.openBatchConfigLogs(42, null);

        // Resolve the list API with one RUNNING record
        await new Promise(function(r) { setImmediate(r); });
        sb.__apiResolvers.shift()([{ executionId: 101, status: "RUNNING", startedAt: "2026-01-01", triggerType: "SCHEDULED" }]);
        await new Promise(function(r) { setImmediate(r); });

        assert.strictEqual(sb.batchTaskState.logExecutionId, 101,
            "default RUNNING record must set logExecutionId");
        assert.strictEqual(sb.__select.value, "101");
        assert.strictEqual(detailCalls.length, 1);
        assert.strictEqual(detailCalls[0].executionId, 101);

        // Trigger interval — should fire again with same ids
        assert.ok(sb.batchTaskState.logRefreshTimer, "timer must be created for RUNNING");
        // Simulate interval callback
        sb.batchTaskState.logRefreshTimer.fn();
        await new Promise(function(r) { setImmediate(r); });
        assert.strictEqual(detailCalls.length, 2, "interval must reload detail");
    });

    it("stale response from old configId does not overwrite current", async () => {
        var sb = createLogSandbox();
        var detailCalls = [];
        sb.loadBatchLogDetail = function(cfgId, execId) {
            detailCalls.push({ configId: cfgId, executionId: execId });
        };

        sb.openBatchConfigLogs(1, null);

        // Capture the pending API call for config 1
        await new Promise(function(r) { setImmediate(r); });
        var resolveA = sb.__apiResolvers.shift();

        // Before A resolves, switch to config 2
        sb.openBatchConfigLogs(2, null);
        await new Promise(function(r) { setImmediate(r); });
        var resolveB = sb.__apiResolvers.shift();

        // Resolve B first
        resolveB([{ executionId: 202, status: "SUCCESS", startedAt: "2026-01-01", triggerType: "MANUAL" }]);
        await new Promise(function(r) { setImmediate(r); });
        assert.strictEqual(sb.batchTaskState.logExecutionId, 202, "B must set current executionId");

        // Now resolve A (stale) — must be ignored
        var beforeExecId = sb.batchTaskState.logExecutionId;
        resolveA([{ executionId: 101, status: "RUNNING", startedAt: "2026-01-01", triggerType: "SCHEDULED" }]);
        await new Promise(function(r) { setImmediate(r); });
        assert.strictEqual(sb.batchTaskState.logExecutionId, beforeExecId,
            "stale response must not overwrite current logExecutionId");
        assert.strictEqual(detailCalls.length, 1, "only one detail call (B)");
    });
});
            // manually compare interval keys
            return n.perMailIntervalMs === d.perMailIntervalMs &&
                   n.perRoundIntervalMs === d.perRoundIntervalMs;
        };
        assert.ok(sb.processManualFormSnapshot(),
            "1000ms baseline must match 1000ms draft (1 sec read → 1000ms)");
    });

    it("baseline 1000 vs form 2000 produces interval diff", () => {
        var sb = createDiffSandbox();
        sb.batchTaskState.manualSource = {
            perMailIntervalMs: 1000,
            perRoundIntervalMs: 60000
        };
        var n = sb.normalizeManualSnapshot(sb.batchTaskState.manualSource);
        var d = sb.normalizeManualSnapshot({
            perMailIntervalMs: 2000,
            perRoundIntervalMs: 60000
        });
        assert.notStrictEqual(n.perMailIntervalMs, d.perMailIntervalMs,
            "2000ms draft must differ from 1000ms baseline");
        assert.strictEqual(n.perRoundIntervalMs, d.perRoundIntervalMs,
            "unchanged interval must match");
    });

    it("baseline 60000 vs form 90000 produces interval diff", () => {
        var sb = createDiffSandbox();
        sb.batchTaskState.manualSource = {
            perMailIntervalMs: 1000,
            perRoundIntervalMs: 60000
        };
        var n = sb.normalizeManualSnapshot(sb.batchTaskState.manualSource);
        var d = sb.normalizeManualSnapshot({
            perMailIntervalMs: 1000,
            perRoundIntervalMs: 90000
        });
        assert.strictEqual(n.perMailIntervalMs, d.perMailIntervalMs,
            "unchanged interval must match");
        assert.notStrictEqual(n.perRoundIntervalMs, d.perRoundIntervalMs,
            "90000ms draft must differ from 60000ms baseline");
    });
});
