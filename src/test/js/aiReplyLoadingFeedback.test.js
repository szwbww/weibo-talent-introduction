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

describe("ai reply model picker (I-1..I-5/S-1)", () => {
    const flashOption = '<option value="DEEPSEEK_V4_FLASH" selected>DeepSeek V4 Flash</option>';
    const proOption = '<option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>';
    const mailboxFlash = '<option value="DEEPSEEK_V4_FLASH">DeepSeek V4 Flash</option>';
    const mailboxPro = '<option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>';

    it("exposes identical Flash/Pro options on both entrances", () => {
        assert.ok(indexSource.includes('id="aiTrainingReplyModel"'));
        assert.ok(indexSource.includes(flashOption));
        assert.ok(indexSource.includes(proOption));
        assert.ok(appSource.includes('id="aiMailboxReplyModel"'));
        assert.ok(appSource.includes(mailboxFlash));
        assert.ok(appSource.includes(mailboxPro));
        assert.ok(appSource.includes('simulateModel: "DEEPSEEK_V4_FLASH"'));
        assert.ok(appSource.includes('selectedModel: "DEEPSEEK_V4_FLASH"'));
        assert.equal((indexSource.match(/DEEPSEEK_V4_/g) || []).length, 2);
        assert.ok(!appSource.includes("DEEPSEEK_V4_REASONER"));
        assert.ok(!indexSource.includes("DEEPSEEK_V4_REASONER"));
    });

    it("sends model snapshots and rejects mismatched selectedModel", () => {
        const simulateFn = appSource.match(/async function runAiTrainingSimulate\(\) \{[\s\S]*?\nasync function /)?.[0] || "";
        assert.ok(simulateFn.includes("model: expectedModel"));
        assert.ok(simulateFn.includes('result.selectedModel !== expectedModel'));
        assert.ok(simulateFn.includes("模型响应与当前选择不一致，请重新生成"));
        assert.ok(simulateFn.includes("expectedModel === currentModel"));
        assert.ok(/action === "ai-reply-turn"[\s\S]*model: expectedModel/.test(appSource));
        assert.ok(/action === "ai-reply-turn"[\s\S]*result\.selectedModel !== expectedModel/.test(appSource));
        assert.ok(/action === "ai-reply-turn"[\s\S]*模型响应与当前选择不一致，请重新生成/.test(appSource));
    });

    it("disables select during loading and restores was-disabled", () => {
        assert.ok(appSource.includes('panel.querySelectorAll("button, textarea, select")'));
        assert.ok(appSource.includes("data-ai-reply-was-disabled"));
    });

    it("keeps model badge out of draft/turns/adopt paths", () => {
        assert.ok(appSource.includes("appendAiChatDraftBubble(result.draftText || \"\")"));
        assert.ok(!/appendAiChatDraftBubble\([^)]*selectedModel/.test(appSource));
        assert.ok(!/appendAiChatDraftBubble\([^)]*模型：/.test(appSource));
        assert.ok(appSource.includes("editor.innerText = draft"));
        assert.ok(!/innerText\s*=\s*[^\n]*模型：/.test(appSource));
        assert.ok(appSource.includes("AI_REPLY_MODEL_LABELS"));
        assert.ok(appSource.includes("`模型：${aiReplyModelLabel(result.selectedModel)}`")
            || appSource.includes("模型：${escapeHtml(aiReplyModelLabel(result.selectedModel))}"));
    });

    it("includes S-1 CSS verbatim and no third option", () => {
        assert.ok(stylesSource.includes(".ai-reply-model-row {"));
        assert.ok(stylesSource.includes("justify-content: flex-end;"));
        assert.ok(stylesSource.includes(".ai-reply-model-select {"));
        assert.ok(stylesSource.includes("min-width: 190px;"));
        assert.ok(stylesSource.includes(".ai-reply-model-select:focus {"));
        assert.ok(stylesSource.includes(".ai-reply-model-select:disabled {"));
        assert.ok(stylesSource.includes("background: #f8fafc;"));
        assert.ok(!/id="aiTrainingReplyModel"[^>]*style=/.test(indexSource));
        assert.ok(!/id="aiMailboxReplyModel"[^>]*style=/.test(appSource));
        assert.equal((appSource.match(/option value="DEEPSEEK_V4_/g) || []).length, 2);
        assert.equal((indexSource.match(/option value="DEEPSEEK_V4_/g) || []).length, 2);
    });

    it("does not reset mailbox model when switching mails", () => {
        const resetFn = appSource.match(/function resetAiReplyState\(recordId\) \{[\s\S]*?\n\}/)?.[0] || "";
        assert.ok(resetFn.includes("aiReplyState.inFlight = false"));
        assert.ok(!resetFn.includes("selectedModel"));
        const initFn = appSource.match(/function initAiReplyWorkbench\(recordId\) \{[\s\S]*?\n\}/)?.[0] || "";
        assert.ok(initFn.includes("modelSelect.value = aiReplyState.selectedModel"));
    });
});
