const assert = require("assert");
const fs = require("fs");
const path = require("path");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const appSource = fs.readFileSync(appJsPath, "utf-8");
const indexSource = fs.readFileSync(indexHtmlPath, "utf-8");
const stylesSource = fs.readFileSync(stylesPath, "utf-8");

/** Mirrors formatUnsupportedRequests in app.js. */
function formatUnsupportedRequests(unsupportedRequests) {
    const items = (unsupportedRequests || []).filter((item) => String(item || "").trim());
    if (items.length === 0) return "";
    const shown = items.slice(0, 3).map((item) => String(item).trim());
    const rest = items.length - shown.length;
    let text = `以下请求缺少已审核依据：${shown.join("；")}`;
    if (rest > 0) {
        text += `；另 ${rest} 项`;
    }
    return text;
}

function buildCoverageLabel(result) {
    const requestCount = Number(result.requestCount) || 0;
    const groundedRequestCount = Number(result.groundedRequestCount) || 0;
    if (requestCount <= 0) return null;
    return `事实覆盖 ${groundedRequestCount}/${requestCount} 项`;
}

function buildSimulatePayload(contactId, mailRecordId, promptOverride) {
    const body = {
        expertContactId: contactId,
        promptOverride: promptOverride || null
    };
    if (mailRecordId != null) {
        body.mailRecordId = mailRecordId;
    }
    return body;
}

describe("ai reply loading helpers source contracts", () => {
    it("uses shared setAiReplyLoading and restores was-disabled markers", () => {
        assert.ok(appSource.includes("function setAiReplyLoading(panel, loading"));
        assert.ok(appSource.includes("data-ai-reply-was-disabled"));
        assert.ok(appSource.includes("setAiReplyLoading(panel, true)"));
        const simulateFn = appSource.match(/async function runAiTrainingSimulate\(\) \{[\s\S]*?\nasync function /)?.[0] || "";
        assert.ok(simulateFn.includes("setAiReplyLoading"));
        assert.ok(!simulateFn.includes("setTagEditorLoading"));
        const mailboxUsesHelper = /action === "ai-reply-turn"[\s\S]*?setAiReplyLoading\(panel, true\)/.test(appSource);
        assert.ok(mailboxUsesHelper);
    });

    it("keeps feedback and draft text isolated", () => {
        assert.ok(appSource.includes("function renderAiReplyFeedback("));
        assert.ok(appSource.includes("事实覆盖"));
        assert.ok(!/已回答\s*\$\{/.test(appSource) && !appSource.includes("已回答 "));
        assert.ok(appSource.includes("appendAiChatDraftBubble(result.draftText"));
        assert.ok(!/appendAiChatDraftBubble\([^)]*contextWarnings/.test(appSource));
        assert.ok(indexSource.includes('id="aiTrainingSimulateFeedback"'));
        assert.ok(appSource.includes('id="aiReplyFeedback"'));
    });

    it("sends mailRecordId with expertContactId when available", () => {
        assert.ok(appSource.includes("selectedSimulateMailRecordId"));
        assert.ok(appSource.includes("body.mailRecordId = mailRecordId"));
        assert.ok(appSource.includes("simulateRequestSeq"));
        assert.ok(appSource.includes("aiReplyState.requestSeq"));
    });

    it("adds S-1/S-2 CSS classes without tag-editor reuse", () => {
        assert.ok(stylesSource.includes(".ai-reply-loading-overlay"));
        assert.ok(stylesSource.includes(".ai-reply-loading-spinner"));
        assert.ok(stylesSource.includes("@keyframes ai-reply-spin"));
        assert.ok(stylesSource.includes(".ai-reply-feedback"));
        assert.ok(stylesSource.includes(".ai-reply-coverage"));
        assert.ok(stylesSource.includes(".ai-reply-warning"));
        assert.ok(stylesSource.includes(".ai-reply-error"));
        assert.ok(/\.ai-reply-section \.ai-chat-panel \{[\s\S]*?position:\s*relative;/.test(stylesSource));
        assert.ok(!stylesSource.includes("ai-reply-loading") || !/ai-reply-loading[\s\S]*tag-editor/.test(stylesSource));
    });
});

describe("ai reply feedback copy (I-4/I-5)", () => {
    it("uses 事实覆盖 wording and truncates unsupported list", () => {
        assert.strictEqual(
            buildCoverageLabel({ requestCount: 7, groundedRequestCount: 6 }),
            "事实覆盖 6/7 项"
        );
        assert.ok(!String(buildCoverageLabel({ requestCount: 7, groundedRequestCount: 6 })).includes("已回答"));
        assert.strictEqual(
            formatUnsupportedRequests(["a", "b", "c", "d"]),
            "以下请求缺少已审核依据：a；b；c；另 1 项"
        );
    });

    it("degrades when backend omits new fields", () => {
        assert.strictEqual(buildCoverageLabel({}), null);
        assert.strictEqual(formatUnsupportedRequests(undefined), "");
    });
});

describe("simulate payload mailRecordId (I-6)", () => {
    it("includes both ids when mailRecordId is available", () => {
        assert.deepStrictEqual(
            buildSimulatePayload(10, 99, ""),
            { expertContactId: 10, promptOverride: null, mailRecordId: 99 }
        );
    });

    it("falls back to contactId only when mailRecordId missing", () => {
        assert.deepStrictEqual(
            buildSimulatePayload(10, null, "note"),
            { expertContactId: 10, promptOverride: "note" }
        );
    });
});
