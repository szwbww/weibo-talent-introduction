const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const stylesCssPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const stylesCssSource = fs.readFileSync(stylesCssPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createSandbox() {
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

function normalizeWhitespace(html) {
    return html.replace(/\s+/g, " ").trim();
}

// S-1 contract block (plan verbatim) with concrete values substituted
const S1_EXPECTED = normalizeWhitespace(`
<div class="detail-section expert-tag-editor" id="expertTagEditor" data-orcid="0000-0001" data-level="CANDIDATE" data-profile-missing="true">
    <div class="inbound-tag-editor-head">
        <h3>专家标签</h3>
    </div>
    <div class="inbound-tag-editor-chips"><span class="muted">该专家在 ES 中无画像文档，标签功能不可用</span></div>
</div>
`);

// S-2 contract block (plan verbatim) with concrete values and empty-tags chips
const S2_EXPECTED = normalizeWhitespace(`
<div class="detail-section expert-tag-editor" id="expertTagEditor" data-orcid="0000-0001" data-level="CANDIDATE">
    <div class="inbound-tag-editor-head">
        <h3>专家标签</h3>
        <div class="inbound-tag-editor-actions">
            <button type="button" class="button primary small" data-action="expert-add-tag-open">+ 添加标签</button>
        </div>
    </div>
    <div class="inbound-tag-editor-chips"><span class="muted">暂无标签</span></div>
</div>
`);

// ──────────────────────────────────────────────────────────────────────────
// SUITE: expert profile absence degrades, never errors (P1 I-1/I-3/I-4/S-1/S-2)
// ──────────────────────────────────────────────────────────────────────────

describe("expertProfileAbsence: found=false renders S-1 degraded editor (I-3/S-1)", () => {
    it("produces the S-1 DOM verbatim: no write actions, muted notice, data-profile-missing", () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", true);

        assert.strictEqual(normalizeWhitespace(html), S1_EXPECTED, "found=false output must match S-1 verbatim");
        assert.ok(!html.includes('data-action="expert-add-tag-open"'), "must NOT contain add-tag button");
        assert.ok(!html.includes('data-action="expert-remove-tag"'), "must NOT contain remove-tag buttons");
        assert.ok(html.includes('data-profile-missing="true"'), "must carry data-profile-missing=true");
        assert.ok(html.includes('class="muted"'), "must carry muted notice class");
        assert.ok(html.includes("该专家在 ES 中无画像文档，标签功能不可用"), "must carry the exact notice text");
        assert.ok(html.includes('data-orcid="0000-0001"') && html.includes('data-level="CANDIDATE"'),
            "container id/data-orcid/data-level preserved (updateExpertTagEditor matching)");
    });
});

describe("expertProfileAbsence: found=true renders S-2 editor verbatim (S-2)", () => {
    it("keeps the present-profile DOM identical to the S-2 baseline", () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", false);

        assert.strictEqual(normalizeWhitespace(html), S2_EXPECTED, "found=true output must match S-2 verbatim");
        assert.ok(html.includes('data-action="expert-add-tag-open"'), "add-tag button must be present");
        assert.ok(!html.includes('data-profile-missing="true"'), "no data-profile-missing on present profiles");
    });
});

describe("expertProfileAbsence: found === undefined falls back to present-profile (I-4)", () => {
    it("fetchExpertTagsFromEs treats a missing found field as found", async () => {
        const sb = createSandbox();
        sb.api = async () => ({ orcidId: "0000-0001", tags: ["承诺回复材料"] });

        const result = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.strictEqual(result.found, true, "undefined found must be treated as present profile");
        assert.deepStrictEqual(result.tags, ["承诺回复材料"]);
    });

    it("renderExpertTagEditor with undefined profileMissing renders the S-2 branch", () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE");

        assert.strictEqual(normalizeWhitespace(html), S2_EXPECTED, "undefined profileMissing must not degrade");
    });
});

describe("expertProfileAbsence: api errors propagate, never degrade to found=false (I-1)", () => {
    it("fetchExpertTagsFromEs rethrows the api error", async () => {
        const sb = createSandbox();
        sb.api = async () => { throw new Error("es down"); };

        await assert.rejects(
            sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE"),
            /es down/
        );
    });

    it("found=false is only produced by an explicit profile response", async () => {
        const sb = createSandbox();
        sb.api = async () => ({ found: false, tags: [] });

        const result = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.strictEqual(result.found, false);
        assert.deepStrictEqual(result.tags, []);
    });
});

// K-dom-stub-tests-hide-dangling-refs: the classes used by S-1 must exist in styles.css
describe("expertProfileAbsence: S-1 classes exist in styles.css", () => {
    it("muted and inbound-tag-editor-chips are real CSS classes", () => {
        assert.ok(stylesCssSource.includes(".muted {"), ".muted must exist in styles.css");
        assert.ok(stylesCssSource.includes(".inbound-tag-editor-chips"), ".inbound-tag-editor-chips must exist in styles.css");
        assert.ok(stylesCssSource.includes(".expert-tag-editor"), ".expert-tag-editor must exist in styles.css");
        assert.ok(stylesCssSource.includes(".inbound-tag-editor-head"), ".inbound-tag-editor-head must exist in styles.css");
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: V-1 repair — a rejected tag fetch degrades to S-1 and reports,
// it never aborts the surrounding detail panel (P1 observable outcomes 1-4 / I-5)
// ──────────────────────────────────────────────────────────────────────────

// Brace-aware extractor: the four renderers contain nested template literals
// (e.g. `${cond ? `...` : "..."}`), so a regex or single-level scanner is not
// enough. Stack machine: base/expr contexts count braces; template-text
// contexts ignore everything except `, \ and ${; strings and comments are
// skipped in code contexts.
function extractFunction(name) {
    const startRe = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{");
    const startMatch = appJsSource.match(startRe);
    if (!startMatch) throw new Error("Could not find function " + name + " in app.js");
    const stack = []; // entries: { kind: "expr", depth: 1 } | { kind: "tpl" }
    let baseDepth = 1; // the function's opening brace
    let i = startMatch.index + startMatch[0].length; // scan past the opening '{'
    let mode = "code"; // code | sgl | dbl | line | block
    for (; i < appJsSource.length; i++) {
        const ch = appJsSource[i];
        const next = appJsSource[i + 1];
        if (mode === "line") {
            if (ch === "\n") mode = "code";
            continue;
        }
        if (mode === "block") {
            if (ch === "*" && next === "/") { mode = "code"; i++; }
            continue;
        }
        if (mode === "sgl") {
            if (ch === "\\") { i++; continue; }
            if (ch === "'") mode = "code";
            continue;
        }
        if (mode === "dbl") {
            if (ch === "\\") { i++; continue; }
            if (ch === '"') mode = "code";
            continue;
        }
        // mode === "code": function body, `${...}` expression, or template text
        const top = stack[stack.length - 1];
        if (top && top.kind === "tpl") {
            if (ch === "\\") { i++; continue; }
            if (ch === "`") { stack.pop(); continue; }
            if (ch === "$" && next === "{") { stack.push({ kind: "expr", depth: 1 }); i++; continue; }
            continue;
        }
        if (ch === "/" && next === "/") { mode = "line"; i++; continue; }
        if (ch === "/" && next === "*") { mode = "block"; i++; continue; }
        if (ch === "'") { mode = "sgl"; continue; }
        if (ch === '"') { mode = "dbl"; continue; }
        if (ch === "`") { stack.push({ kind: "tpl" }); continue; }
        if (ch === "{") {
            if (top && top.kind === "expr") top.depth++;
            else baseDepth++;
            continue;
        }
        if (ch === "}") {
            if (top && top.kind === "expr") {
                top.depth--;
                if (top.depth === 0) stack.pop();
                continue;
            }
            baseDepth--;
            if (baseDepth === 0) return appJsSource.slice(startMatch.index, i + 1);
        }
    }
    throw new Error("Unbalanced function body for " + name);
}

const NOTICE_TEXT = "该专家在 ES 中无画像文档，标签功能不可用";

function createRendererSandbox() {
    const elements = {};
    const statusCalls = [];
    function makeEl() {
        return {
            hidden: false,
            innerHTML: "",
            scrollTop: 0,
            value: "CANDIDATE",
            dataset: {},
            classList: {
                add() {}, remove() {}, toggle() {}, contains() { return false; }
            },
            addEventListener() {}, removeEventListener() {},
            querySelector() { return null; },
            showModal() {}, close() {}
        };
    }
    function $(sel) {
        if (!elements[sel]) elements[sel] = makeEl();
        return elements[sel];
    }
    const sandbox = {
        $,
        statusCalls,
        showStatus(message, type) { statusCalls.push([message, type]); },
        api: async (url) => {
            if (String(url).includes("/api/experts/profile")) {
                throw new Error("es down");
            }
            if (String(url).includes("/api/mail/unmatched-inbound/")) {
                return {
                    record: { id: 5, expertContactId: 1, expertName: "Dr. Test", expertEmail: "t@d.cn", orcidId: "0000-0001", subject: "Re: materials", fromEmail: "t@d.cn", receivedAt: "2026-08-07T10:00:00", senderAccountCode: "acc", body: "hi", cleanedBody: "hi" },
                    contact: { orcidId: "0000-0001", currentIndexLevel: "CANDIDATE", expertName: "Dr. Test", expertEmail: "t@d.cn", operatorStatus: "ACTIVE" },
                    candidates: []
                };
            }
            if (String(url).includes("/api/mail/mailbox/")) {
                return {
                    timestamp: "2026-08-07T10:00:00",
                    direction: "INBOUND",
                    mailType: "INTRODUCTION",
                    hasAttachment: false,
                    body: "Hello",
                    subject: "Re: hi",
                    senderAccountCode: "acc",
                    expertEmail: "t@d.cn",
                    expertName: "Dr. Test",
                    expertOrcidId: "0000-0001",
                    inboundProcessingId: null
                };
            }
            if (String(url).includes("/api/expert-contacts/")) {
                return {
                    contact: {
                        id: 1,
                        expertName: "Dr. Test",
                        expertEmail: "t@d.cn",
                        orcidId: "0000-0001",
                        currentIndexLevel: "CANDIDATE",
                        operatorStatus: "ACTIVE",
                        currentStatus: "MANUAL_REVIEW",
                        mails: []
                    },
                    mails: []
                };
            }
            return {};
        },
        loadMailSendOptions: async () => [],
        unmountMailboxTrustReplyHosts() {},
        mountLiveTrustReply() {},
        mountAutoPreviewTrustReply() {},
        loadEmailAliases() {},
        resetPreflightState() {},
        focusMailboxProcessingPanel() {},
        requestAnimationFrame() {},
        backToListBtnHtml: () => "",
        renderDetailSubTabs: () => "",
        renderAcademicProfilePanel: () => "",
        renderManualAttentionBanner: () => "",
        renderMailSendOptionGroups: () => "",
        renderMailItem: () => "",
        renderMeetingSchedule: () => "",
        renderKeywords: () => "",
        renderExpertDocuments: () => "",
        renderOperatorLogs: () => "",
        renderMailboxInboundTagEditor: () => "",
        formatStatusTransition: () => "",
        formatMailTime: () => "",
        translatableBody: (body) => String(body || ""),
        labelMailDirection: () => "in",
        labelMailType: () => "intro",
        badge: () => "",
        labelStatus: () => "?",
        optionsFromArray: () => "",
        indexLevelLabels: {},
        operatorStatusLabels: {},
        operatorStatusOptions: [],
        indexLevelOptions: [],
        URLSearchParams,
        liveDetailLoadSeq: 1,
        state: { contacts: [], selectedExpertOrcid: null, mailbox: { detailContext: null } }
    };
    vm.createContext(sandbox);
    vm.runInContext(`
        let manualReplyQaContext = null;
        let aiReplyState = { adoptContext: null };
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
    vm.runInContext(extractFunction("fetchExpertTagsFromEs"), sandbox);
    vm.runInContext(extractFunction("renderExpertTagEditor"), sandbox);
    vm.runInContext(extractFunction("renderMailboxExpertTagEditor"), sandbox);
    vm.runInContext(extractFunction("showExpertDetail"), sandbox);
    vm.runInContext(extractFunction("loadContactDetail"), sandbox);
    // P2: loadContactDetail now initializes the sender-binding dirty gate after filling the select
    vm.runInContext(extractFunction("updateSenderBindingDirtyState"), sandbox);
    vm.runInContext(extractFunction("showMailDetail"), sandbox);
    vm.runInContext(extractFunction("showUnmatchedDetail"), sandbox);
    return { sandbox, elements, statusCalls };
}

describe("expertProfileAbsence: rejected tag fetch degrades to S-1 and reports (V-1 repair)", () => {
    it("showExpertDetail completes and renders the S-1 notice when the tag fetch rejects", async () => {
        const { sandbox, elements, statusCalls } = createRendererSandbox();
        await sandbox.showExpertDetail({ orcidId: "0000-0001", displayName: "Dr. Test", email: "t@d.cn", indexLevel: "CANDIDATE" });

        const html = elements["#contactDetail"].innerHTML;
        assert.ok(html.length > 0, "detail panel must be populated");
        assert.ok(html.includes(NOTICE_TEXT), "panel must render the profile-missing notice");
        assert.deepStrictEqual(statusCalls, [["es down", "error"]], "failure must be reported via showStatus(error)");
    });

    it("loadContactDetail completes and renders the S-1 notice when the tag fetch rejects", async () => {
        const { sandbox, elements, statusCalls } = createRendererSandbox();
        await sandbox.loadContactDetail(1);

        const html = elements["#contactDetail"].innerHTML;
        assert.ok(html.length > 0, "detail panel must be populated");
        assert.ok(html.includes(NOTICE_TEXT), "panel must render the profile-missing notice");
        assert.deepStrictEqual(statusCalls, [["es down", "error"]], "failure must be reported via showStatus(error)");
    });

    it("showMailDetail read-only branch keeps the panel rendered with the S-1 notice when the tag fetch rejects", async () => {
        const { sandbox, elements, statusCalls } = createRendererSandbox();
        await sandbox.showMailDetail("INBOUND_PROCESSING", 42);

        const panel = elements["#unmatchedDetailPanel"];
        assert.strictEqual(panel.hidden, false, "panel must be shown");
        assert.ok(panel.innerHTML.length > 0, "panel must be populated");
        assert.ok(panel.innerHTML.includes(NOTICE_TEXT), "panel must render the profile-missing notice");
        assert.deepStrictEqual(statusCalls, [["es down", "error"]], "failure must be reported via showStatus(error)");
    });

    it("showUnmatchedDetail keeps the panel rendered with the S-1 notice when the tag fetch rejects", async () => {
        const { sandbox, elements, statusCalls } = createRendererSandbox();
        await sandbox.showUnmatchedDetail(5);

        const panel = elements["#unmatchedDetailPanel"];
        assert.strictEqual(panel.hidden, false, "panel must be shown");
        assert.ok(panel.innerHTML.length > 0, "panel must be populated");
        assert.ok(panel.innerHTML.includes(NOTICE_TEXT), "panel must render the profile-missing notice");
        assert.deepStrictEqual(statusCalls, [["es down", "error"]], "failure must be reported via showStatus(error)");
    });
});

// ──────────────────────────────────────────────────────────────────────────
// SUITE: P2 S-7 — inline layout mode for the detail-header name row
// (plan T11: +1 describe / 4 cases; CSS existence folded into case 1)
// ──────────────────────────────────────────────────────────────────────────

describe("expertProfileAbsence: inline layout (P2 S-7)", () => {
    it('layout="inline" + profileMissing=false renders the inline editor (S-7/I-6)', () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor(["承诺回复材料"], "0000-0001", "CANDIDATE", "expertTagEditor", false, "inline");

        assert.ok(html.includes('class="expert-tag-editor is-inline"'), "inline root class must be present");
        assert.ok(html.includes('data-layout="inline"'), "data-layout=inline must be present");
        assert.ok(html.includes('data-action="expert-add-tag-open"'), "add-tag button must be present");
        assert.ok(!html.includes("detail-section"), "inline output must NOT contain detail-section");
        assert.ok(!html.includes("<h3>"), "inline output must NOT contain a section h3 heading");

        // K-dom-stub-tests-hide-dangling-refs: the P2 inline classes must exist in styles.css
        assert.ok(stylesCssSource.includes(".expert-tag-editor.is-inline"), ".expert-tag-editor.is-inline must exist in styles.css");
        assert.ok(stylesCssSource.includes(".expert-tag-nodoc"), ".expert-tag-nodoc must exist in styles.css");
        assert.ok(stylesCssSource.includes(".expert-tag-add-btn"), ".expert-tag-add-btn must exist in styles.css");
    });

    it('layout="inline" + profileMissing=true renders the nodoc pill without actions', () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", true, "inline");

        assert.ok(html.includes('data-profile-missing="true"'), "data-profile-missing=true must be present");
        assert.ok(html.includes("expert-tag-nodoc"), "expert-tag-nodoc pill must be present");
        assert.ok(html.includes("ES 无画像"), "nodoc pill text must be ES 无画像");
        assert.ok(!html.includes("data-action="), "inline nodoc branch must NOT contain any data-action");
    });

    it("both inline outputs keep id/data-orcid/data-level (I-6)", () => {
        const sb = createSandbox();
        const htmlPresent = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", false, "inline");
        const htmlMissing = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", true, "inline");

        for (const html of [htmlPresent, htmlMissing]) {
            assert.ok(html.includes('id="expertTagEditor"'), "editor id must be preserved");
            assert.ok(html.includes('data-orcid="0000-0001"'), "data-orcid must be preserved");
            assert.ok(html.includes('data-level="CANDIDATE"'), "data-level must be preserved");
        }
    });

    it("non-inline layout values keep the S-2 output byte-identical (I-5)", () => {
        const sb = createSandbox();
        for (const layout of [undefined, "section", "whatever"]) {
            const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", false, layout);
            assert.strictEqual(normalizeWhitespace(html), S2_EXPECTED, "layout=" + layout + " must match S-2 verbatim");
        }
    });
});
