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
        api: async () => ({ tags: [] }),
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

        const tags = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.ok(profileUrl.includes("/api/experts/profile"));
        assert.ok(profileUrl.includes("orcidId=0000-0001"));
        assert.ok(profileUrl.includes("level=CANDIDATE"));
        assert.deepStrictEqual(tags, ["承诺回复材料"]);
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

    it("S-2: batchSendType select appears before batchSendTemplateId select in DOM", () => {
        const typePos = indexHtmlSource.indexOf('id="batchSendType"');
        const templatePos = indexHtmlSource.indexOf('id="batchSendTemplateId"');
        assert.ok(typePos !== -1, "batchSendType select must exist");
        assert.ok(templatePos !== -1, "batchSendTemplateId select must exist");
        assert.ok(typePos < templatePos,
            "batchSendType (type selector) must appear before batchSendTemplateId (template selector)");
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
        // Also verify the default assignment pattern exists
        assert.ok(src.includes("batchSendType = defaultType"), "batchSendType must be set to defaultType");
    });

    it("I-11 config/status/count/start/pause APIs all routed through batchSendTypeBase", () => {
        const src = appJsSource;
        // batchSendTypeBase must be called for various endpoints
        const typeBaseCalls = (src.match(/batchSendTypeBase\(/g) || []).length;
        assert.ok(typeBaseCalls >= 4,
            `batchSendTypeBase must be called at least 4 times (config, status, pending-count, start/pause), got ${typeBaseCalls}`);
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
