const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const stylesCssPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractConst(name) {
    const regex = new RegExp("const\\s+" + name + "\\s*=\\s*\\{[\\s\\S]*?\\n\\};");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find const " + name + " in app.js");
    return match[0];
}

function createSandbox() {
    const sandbox = {
        AI_REPLY_MODEL_LABELS: {
            DEEPSEEK_V4_FLASH: "DeepSeek V4 Flash",
            DEEPSEEK_V4_PRO: "DeepSeek V4 Pro"
        },
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;")
    };
    vm.createContext(sandbox);
    vm.runInContext(extractConst("AI_REPLY_WARNING_LABELS"), sandbox);
    vm.runInContext("this.AI_REPLY_WARNING_LABELS = AI_REPLY_WARNING_LABELS;", sandbox);
    vm.runInContext(extractFn("aiReplyModelLabel"), sandbox);
    vm.runInContext(extractFn("aiReplyGenerationStateLabel"), sandbox);
    vm.runInContext(extractFn("formatUnsupportedRequests"), sandbox);
    vm.runInContext(extractFn("collapseAiReplyRequestText"), sandbox);
    vm.runInContext(extractFn("summarizeAiReplyCoverage"), sandbox);
    vm.runInContext(extractFn("formatAiReplyReviewWarnings"), sandbox);
    vm.runInContext(extractFn("renderAiReplyFeedback"), sandbox);
    return sandbox;
}

describe("aiReplyGenerationStateLabel", () => {
    it("maps fixed Chinese labels for all four states", () => {
        const { aiReplyGenerationStateLabel } = createSandbox();
        assert.strictEqual(aiReplyGenerationStateLabel("LLM_USED"), "模型已生成");
        assert.strictEqual(
            aiReplyGenerationStateLabel("FALLBACK_LLM_DISABLED"),
            "LLM 已关闭—结构化规则草稿"
        );
        assert.strictEqual(
            aiReplyGenerationStateLabel("FALLBACK_CLIENT_UNAVAILABLE"),
            "模型客户端不可用—结构化规则草稿"
        );
        assert.strictEqual(
            aiReplyGenerationStateLabel("FALLBACK_NO_RESPONSE"),
            "模型无有效响应—结构化规则草稿"
        );
        assert.strictEqual(aiReplyGenerationStateLabel("UNKNOWN"), "");
    });
});

describe("AI_REPLY_WARNING_LABELS", () => {
    it("maps UNAUTHORIZED_ACTION_REMOVED to Chinese without raw code", () => {
        const { AI_REPLY_WARNING_LABELS } = createSandbox();
        const label = AI_REPLY_WARNING_LABELS.UNAUTHORIZED_ACTION_REMOVED;
        assert.ok(label);
        assert.notStrictEqual(label, "UNAUTHORIZED_ACTION_REMOVED");
        assert.match(label, /未授权/);
    });
});

describe("renderAiReplyFeedback generationState", () => {
    it("renders LLM_USED with coverage class and fallback with warning class", () => {
        const sandbox = createSandbox();
        const container = { hidden: true, innerHTML: "" };

        sandbox.renderAiReplyFeedback(container, {
            generationState: "LLM_USED",
            requestCount: 0,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.strictEqual(container.hidden, false);
        assert.match(container.innerHTML, /class="ai-reply-coverage"/);
        assert.match(container.innerHTML, /模型已生成/);
        assert.doesNotMatch(container.innerHTML, /class="pre"/);

        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_LLM_DISABLED",
            requestCount: 0,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-warning"/);
        assert.match(container.innerHTML, /LLM 已关闭—结构化规则草稿/);
        assert.doesNotMatch(container.innerHTML, /class="pre"/);
    });

    it("keeps generationState out of draft/adopt payload surfaces in app.js", () => {
        const draftBubble = extractFn("appendAiChatDraftBubble");
        assert.doesNotMatch(draftBubble, /generationState/);

        const adoptIdx = appJsSource.indexOf('if (action === "ai-adopt-draft")');
        assert.ok(adoptIdx > 0);
        const adoptBlock = appJsSource.slice(adoptIdx, adoptIdx + 1200);
        assert.doesNotMatch(adoptBlock, /generationState/);

        const turnPayloadIdx = appJsSource.indexOf("turns: turnsToSend");
        assert.ok(turnPayloadIdx > 0);
        const turnPayload = appJsSource.slice(turnPayloadIdx - 200, turnPayloadIdx + 400);
        assert.doesNotMatch(turnPayload, /generationState/);
    });

    it("does not require new CSS classes or index.html markup for generationState", () => {
        const styles = fs.readFileSync(stylesCssPath, "utf-8");
        const indexHtml = fs.readFileSync(indexHtmlPath, "utf-8");
        assert.doesNotMatch(styles, /generationState|ai-reply-generation/);
        assert.doesNotMatch(indexHtml, /generationState|ai-reply-generation/);
        assert.match(styles, /\.ai-reply-coverage/);
        assert.match(styles, /\.ai-reply-warning/);
        assert.match(styles, /\.ai-meta-chip/);
    });

    it("replaces DeepSeek unavailable toast with shared generationState label", () => {
        assert.doesNotMatch(appJsSource, /DeepSeek 不可用/);
        assert.match(appJsSource, /aiReplyGenerationStateLabel\(result\.generationState\)/);
        assert.match(appJsSource, /模型已生成/);
    });
});

describe("ai reply loading helpers source contracts", () => {
    it("uses shared setAiReplyLoading and restores was-disabled markers", () => {
        assert.ok(appJsSource.includes("function setAiReplyLoading(panel, loading"));
        assert.ok(appJsSource.includes("data-ai-reply-was-disabled"));
        assert.ok(appJsSource.includes("setAiReplyLoading(panel, true)"));
        const simulateFn = appJsSource.match(/async function runAiTrainingSimulate\(\) \{[\s\S]*?\nasync function /)?.[0] || "";
        assert.ok(simulateFn.includes("setAiReplyLoading"));
        assert.ok(!simulateFn.includes("setTagEditorLoading"));
        const mailboxUsesHelper = /action === "ai-reply-turn"[\s\S]*?setAiReplyLoading\(panel, true\)/.test(appJsSource);
        assert.ok(mailboxUsesHelper);
    });

    it("keeps feedback and draft text isolated", () => {
        assert.ok(appJsSource.includes("function renderAiReplyFeedback("));
        assert.ok(appJsSource.includes("依据覆盖：完整"));
        assert.ok(!/已回答\s*\$\{/.test(appJsSource) && !appJsSource.includes("已回答 "));
        assert.ok(appJsSource.includes("appendAiChatDraftBubble(rawDraft, renderedDraft, result.requestCoverage || [])"));
        assert.ok(!/appendAiChatDraftBubble\([^)]*contextWarnings/.test(appJsSource));
        assert.ok(fs.readFileSync(indexHtmlPath, "utf-8").includes('id="aiTrainingSimulateFeedback"'));
        assert.ok(appJsSource.includes('id="aiReplyFeedback"'));
    });

    it("routes display/copy/adopt to rendered and turns to raw template", () => {
        assert.match(appJsSource, /lastDraftTemplate:\s*""/);
        assert.match(appJsSource, /lastRenderedDraft:\s*""/);
        assert.doesNotMatch(appJsSource, /lastDraft:\s*""/);
        assert.match(
            appJsSource,
            /translatableBody\(result\.renderedDraftText \|\| result\.draftText/
        );
        assert.match(
            appJsSource,
            /sim\?\.renderedDraftText \|\| sim\?\.draftText/
        );
        assert.match(appJsSource, /assistantDraft:\s*aiReplyState\.lastDraftTemplate/);
        assert.match(appJsSource, /aiReplyState\.lastDraftTemplate\s*=\s*rawDraft/);
        assert.match(appJsSource, /aiReplyState\.lastRenderedDraft\s*=\s*renderedDraft/);
        assert.match(appJsSource, /aiReplyState\.drafts\[draftId\]\s*=\s*\{[\s\S]*?needsGroundingReview/);
        assert.match(appJsSource, /entry\?\.rendered\s*\?\?\s*aiReplyState\.lastRenderedDraft/);
        assert.match(appJsSource, /editor\.innerText\s*=\s*rendered/);
        assert.doesNotMatch(appJsSource, /assistantDraft:\s*aiReplyState\.lastRenderedDraft/);
        assert.doesNotMatch(appJsSource, /assistantDraft:\s*aiReplyState\.lastDraft[^T]/);
    });

    it("preserves raw template across adopt→send only when editor matches baseline", () => {
        assert.match(appJsSource, /adoptContext:\s*null/);
        assert.match(appJsSource, /aiReplyState\.adoptContext\s*=\s*null/);
        assert.match(appJsSource, /rawTemplate:\s*raw\s*\|\|\s*""/);
        assert.match(appJsSource, /renderedBaselineHtml:\s*editor\s*\?\s*editor\.innerHTML/);
        assert.match(appJsSource, /templateTextBody\s*=\s*adopt\.rawTemplate/);
        const sendIdx = appJsSource.indexOf('if (action === "send-manual-rich-reply")');
        assert.ok(sendIdx > 0);
        const sendBlock = appJsSource.slice(sendIdx, sendIdx + 2200);
        assert.match(sendBlock, /editor\.innerText\.trim\(\)\s*===\s*\(adopt\.renderedBaseline/);
        assert.match(sendBlock, /editor\.innerHTML\s*===\s*\(adopt\.renderedBaselineHtml/);
        assert.match(sendBlock, /htmlBody:\s*editor\.innerHTML/);
        assert.match(sendBlock, /textBody:\s*editor\.innerText/);
        assert.doesNotMatch(fs.readFileSync(stylesCssPath, "utf-8"), /templateTextBody|adoptContext/);
        assert.doesNotMatch(fs.readFileSync(indexHtmlPath, "utf-8"), /templateTextBody|adoptContext/);
    });

    it("omits raw template when rich-format HTML changes without text change", () => {
        const adoptIdx = appJsSource.indexOf('if (action === "ai-adopt-draft")');
        assert.ok(adoptIdx > 0);
        const adoptBlock = appJsSource.slice(adoptIdx, adoptIdx + 1600);
        assert.match(adoptBlock, /renderedBaselineHtml/);
        const sendIdx = appJsSource.indexOf('if (action === "send-manual-rich-reply")');
        const sendBlock = appJsSource.slice(sendIdx, sendIdx + 2200);
        // Both text and HTML must match — HTML-only format edits must not pass raw.
        assert.match(sendBlock, /innerText\.trim\(\)\s*===\s*\(adopt\.renderedBaseline/);
        assert.match(sendBlock, /innerHTML\s*===\s*\(adopt\.renderedBaselineHtml/);
    });

    it("maps preview warning codes to Chinese labels", () => {
        const { AI_REPLY_WARNING_LABELS } = createSandbox();
        assert.strictEqual(
            AI_REPLY_WARNING_LABELS.AI_REPLY_PREVIEW_ACCOUNT_NOT_FOUND,
            "无法确定回信账号，变量预览未完全渲染"
        );
        assert.strictEqual(
            AI_REPLY_WARNING_LABELS.AI_REPLY_PREVIEW_INVALID_PLACEHOLDER,
            "草稿含未知变量占位符，已保留原文"
        );
    });

    it("sends mailRecordId with expertContactId when available", () => {
        assert.ok(appJsSource.includes("selectedSimulateMailRecordId"));
        assert.ok(appJsSource.includes("body.mailRecordId = mailRecordId"));
        assert.ok(appJsSource.includes("simulateRequestSeq"));
        assert.ok(appJsSource.includes("aiReplyState.requestSeq"));
    });

    it("keeps S-1/S-2 CSS classes without tag-editor reuse", () => {
        const stylesSource = fs.readFileSync(stylesCssPath, "utf-8");
        assert.ok(stylesSource.includes(".ai-reply-loading-overlay"));
        assert.ok(stylesSource.includes(".ai-reply-loading-spinner"));
        assert.ok(stylesSource.includes("@keyframes ai-reply-spin"));
        assert.ok(stylesSource.includes(".ai-reply-feedback"));
        assert.ok(stylesSource.includes(".ai-reply-coverage"));
        assert.ok(stylesSource.includes(".ai-reply-warning"));
        assert.ok(stylesSource.includes(".ai-reply-error"));
        assert.ok(/\.ai-reply-section \.ai-chat-panel \{[\s\S]*?position:\s*relative;/.test(stylesSource));
    });
});

describe("requestCoverage feedback helpers", () => {
    it("summarizes 4 grounded 1 partial 2 unsupported and formats fixed Chinese warnings", () => {
        const sandbox = createSandbox();
        const coverage = [
            { index: 1, requestText: "Q1", status: "GROUNDED" },
            { index: 2, requestText: "Q2", status: "GROUNDED" },
            { index: 3, requestText: "Q3", status: "GROUNDED" },
            { index: 4, requestText: "Q4", status: "GROUNDED" },
            { index: 5, requestText: "Deliverables?", status: "PARTIAL" },
            { index: 6, requestText: "Unknown A?", status: "UNSUPPORTED" },
            { index: 7, requestText: "Unknown B?", status: "UNSUPPORTED" }
        ];
        const summary = sandbox.summarizeAiReplyCoverage(coverage);
        assert.strictEqual(summary.grounded, 4);
        assert.strictEqual(summary.partial, 1);
        assert.strictEqual(summary.unsupported, 2);
        assert.strictEqual(summary.needsGroundingReview, true);
        const warnings = sandbox.formatAiReplyReviewWarnings(summary);
        assert.strictEqual(warnings.length, 3);
        assert.strictEqual(warnings[0], "第 5 项仅部分有已审核依据：Deliverables?；请人工补充后再发送。");
        assert.strictEqual(warnings[1], "第 6 项缺少已审核依据：Unknown A?；草稿未回答该项。");
        assert.strictEqual(warnings[2], "第 7 项缺少已审核依据：Unknown B?；草稿未回答该项。");
    });

    it("collapses whitespace truncates to 240 and escapes HTML in feedback", () => {
        const sandbox = createSandbox();
        const long = `  hello   ${"x".repeat(300)} <script>  `;
        const collapsed = sandbox.collapseAiReplyRequestText(long);
        assert.ok(!collapsed.includes("  "));
        assert.strictEqual(collapsed.length, 240);
        assert.ok(!collapsed.includes("<script>"));
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCoverage: [
                { index: 1, requestText: "A <b>bold</b>", status: "UNSUPPORTED" }
            ],
            contextWarnings: [],
            unsupportedRequests: ["should-not-appear"]
        });
        assert.match(container.innerHTML, /依据覆盖：完整 0 项 · 部分 0 项 · 缺失 1 项/);
        assert.match(container.innerHTML, /第 1 项缺少已审核依据：A &lt;b&gt;bold&lt;\/b&gt;/);
        assert.doesNotMatch(container.innerHTML, /should-not-appear/);
        assert.doesNotMatch(container.innerHTML, /<b>bold<\/b>/);
    });

    it("falls back to unsupportedRequests when requestCoverage missing", () => {
        const sandbox = createSandbox();
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCount: 2,
            groundedRequestCount: 1,
            contextWarnings: [],
            unsupportedRequests: ["No coverage field"]
        });
        assert.match(container.innerHTML, /事实覆盖 1\/2 项/);
        assert.match(container.innerHTML, /以下请求缺少已审核依据：No coverage field/);
    });

    it("maps structured invalid warning without leaking raw status tokens as body", () => {
        const sandbox = createSandbox();
        assert.strictEqual(
            sandbox.AI_REPLY_WARNING_LABELS.AI_REPLY_STRUCTURED_RESPONSE_INVALID,
            "模型返回格式无效，已使用审核依据生成结构化草稿。"
        );
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCoverage: [{ index: 1, requestText: "Q", status: "WEIRD" }],
            contextWarnings: ["AI_REPLY_STRUCTURED_RESPONSE_INVALID"],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /部分请求覆盖状态未知/);
        assert.doesNotMatch(container.innerHTML, />WEIRD</);
        assert.match(container.innerHTML, /模型返回格式无效/);
    });
});

describe("coverage isolation and send guard contracts", () => {
    it("binds review state per draft and copies it on adopt", () => {
        const draftBubble = extractFn("appendAiChatDraftBubble");
        assert.match(draftBubble, /needsGroundingReview/);
        assert.match(draftBubble, /reviewItems/);
        assert.doesNotMatch(draftBubble, /依据覆盖/);
        assert.doesNotMatch(draftBubble, /ai-reply-warning/);
        const adoptIdx = appJsSource.indexOf('if (action === "ai-adopt-draft")');
        const adoptBlock = appJsSource.slice(adoptIdx, adoptIdx + 1800);
        assert.match(adoptBlock, /needsGroundingReview:\s*!!entry\?\.needsGroundingReview/);
        assert.match(adoptBlock, /reviewItems:\s*Array\.isArray\(entry\?\.reviewItems\)/);
        assert.doesNotMatch(adoptBlock, /依据覆盖/);
    });

    it("blocks unmodified gap draft before API and allows after text or HTML edit", () => {
        const sendIdx = appJsSource.indexOf('if (action === "send-manual-rich-reply")');
        assert.ok(sendIdx > 0);
        const sendBlock = appJsSource.slice(sendIdx, sendIdx + 2800);
        assert.match(sendBlock, /needsGroundingReview/);
        assert.match(
            sendBlock,
            /草稿仍有未完整覆盖的问题，请先人工补充或修改正文/
        );
        const guardIdx = sendBlock.indexOf("needsGroundingReview");
        const apiIdx = sendBlock.indexOf("/manual-rich-reply");
        assert.ok(guardIdx >= 0, "guard missing");
        assert.ok(apiIdx > guardIdx, `api=${apiIdx} guard=${guardIdx}`);
        assert.match(sendBlock, /editor\.innerText\s*===\s*\(adopt\.renderedBaseline/);
        assert.match(sendBlock, /editor\.innerHTML\s*===\s*\(adopt\.renderedBaselineHtml/);
        assert.doesNotMatch(sendBlock, /qaRuleIds:\s*.*reviewItems/);
        assert.doesNotMatch(sendBlock, /freeTextPreview:\s*.*依据覆盖/);
    });

    it("keeps coverage out of clipboard continuation and payload text paths", () => {
        assert.doesNotMatch(
            appJsSource,
            /sim\?\.renderedDraftText[\s\S]{0,80}依据覆盖/
        );
        assert.doesNotMatch(
            appJsSource,
            /assistantDraft:[\s\S]{0,80}requestCoverage/
        );
        assert.doesNotMatch(
            appJsSource,
            /templateTextBody[\s\S]{0,120}needsGroundingReview/
        );
        assert.doesNotMatch(fs.readFileSync(stylesCssPath, "utf-8"), /needsGroundingReview|reviewItems/);
        assert.doesNotMatch(fs.readFileSync(indexHtmlPath, "utf-8"), /needsGroundingReview|reviewItems/);
        assert.match(fs.readFileSync(stylesCssPath, "utf-8"), /\.ai-reply-coverage/);
        assert.match(fs.readFileSync(indexHtmlPath, "utf-8"), /id="aiTrainingSimulateFeedback"/);
    });
});
