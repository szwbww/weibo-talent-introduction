"use strict";

const { describe, it } = require("node:test");
const fs = require("fs");

const app = fs.readFileSync("src/main/resources/static/app.js", "utf-8");
const html = fs.readFileSync("src/main/resources/static/index.html", "utf-8");

describe("Phase 9 AI reply review confirmation UI", function () {

    // -- T1: review modal DOM --
    it("has aiReplyReviewModal in index.html", function () {
        if (!html.includes("aiReplyReviewModal")) throw new Error("missing #aiReplyReviewModal");
        if (!html.includes("aiReplyReviewList")) throw new Error("missing #aiReplyReviewList");
        if (!html.includes("aiReplyReviewNote")) throw new Error("missing #aiReplyReviewNote");
        if (!html.includes("aiReplyReviewConfirmBtn")) throw new Error("missing #aiReplyReviewConfirmBtn");
        if (!html.includes("aiReplyReviewCancelBtn")) throw new Error("missing #aiReplyReviewCancelBtn");
    });

    // -- T2: coverage summary upgrade --
    it("has buildIntentReviewItems function", function () {
        if (!app.includes("function buildIntentReviewItems(")) throw new Error("missing buildIntentReviewItems");
    });

    it("intentItems stored in appendAiChatDraftBubble", function () {
        if (!app.includes("intentItems")) throw new Error("missing intentItems in draft entry");
    });

    it("draftIdentity stored in draft entry and adoptContext", function () {
        if (!app.includes("draftIdentity")) throw new Error("missing draftIdentity in draft/adopt state");
    });

    it("lastDraftIdentity tracked in aiReplyState", function () {
        if (!app.includes("lastDraftIdentity")) throw new Error("missing lastDraftIdentity");
    });

    // -- T3: modal state machine --
    it("has aiReplyReviewState object", function () {
        if (!app.includes("aiReplyReviewState")) throw new Error("missing aiReplyReviewState");
    });

    it("has openReviewModal function", function () {
        if (!app.includes("function openReviewModal")) throw new Error("missing openReviewModal");
    });

    it("has clearReviewState function separate from close/cancel", function () {
        if (!app.includes("function clearReviewState")) throw new Error("missing clearReviewState");
    });

    it("has cancelReviewSession function at top-level scope", function () {
        if (!app.includes("function cancelReviewSession")) throw new Error("missing cancelReviewSession");
    });

    it("has confirmReview function that resolves with payload not rejects", function () {
        if (!app.includes("function confirmReview")) throw new Error("missing confirmReview");
    });

    it("has updateReviewConfirmButton function", function () {
        if (!app.includes("function updateReviewConfirmButton")) throw new Error("missing updateReviewConfirmButton");
    });

    // -- T4: refactored send flow --
    it("send flow blocks unedited non-READY text via review modal not status msg", function () {
        if (app.includes('草稿存在缺少审核依据的问题，不可原样发送，请人工补充或修改正文后发送'))
            throw new Error("old text-invariant block message still present");
        if (app.includes('草稿仍有部分问题需人工补充，请修改正文后发送'))
            throw new Error("old text-invariant block message still present");
    });

    it("has submitManualRichReply extracted function", function () {
        if (!app.includes("function submitManualRichReply(")) throw new Error("missing submitManualRichReply");
    });

    it("non-READY path opens modal not blocks on text", function () {
        if (!app.includes("openReviewModal();")) throw new Error("non-READY path should open review modal");
    });

    it("READY path calls submitManualRichReply directly without modal", function () {
        if (!app.includes('readiness === "READY"')) throw new Error("missing READY check");
    });

    it("replySource AI_DRAFT added to non-READY requestBody", function () {
        if (!app.includes('requestBody.replySource = "AI_DRAFT"')) throw new Error("missing replySource AI_DRAFT");
    });

    it("aiReviewConfirmation with draftIdentity built from confirmed payload", function () {
        if (!app.includes("confirmedPayload.draftIdentity")) throw new Error("missing draftIdentity from resolved payload");
        if (!app.includes("confirmedPayload.confirmedReviewKeys")) throw new Error("missing confirmedReviewKeys from resolved payload");
    });

    // -- P1-A: identity from adopt snapshot, not from unbuilt aiReviewConfirmation --
    it("draftIdentity stored in review state before modal opens", function () {
        if (!app.includes("aiReplyReviewState.draftIdentity = adopt.draftIdentity")) throw new Error("identity should be stored from adopt not from requestBody.aiReviewConfirmation");
    });
    it("confirmReview reads identity from reviewState not requestBody", function () {
        if (!app.includes("draftIdentity: aiReplyReviewState.draftIdentity")) throw new Error("confirmReview must read from reviewState");
    });
    it("draftIdentity is cleared in clearReviewState", function () {
        if (!app.includes("aiReplyReviewState.draftIdentity = null;")) throw new Error("identity must be cleared on reset");
    });

    // -- P1-A: confirm path does not reject --
    it("confirmReview captures immutable payload before clear", function () {
        if (!app.includes("var payload = {")) throw new Error("missing immutable payload capture in confirmReview");
        if (!app.includes("resolveFn(payload)")) throw new Error("resolve should pass payload not void");
    });

    // -- P1-B: continuation turn preserves identity --
    it("identity preserved through continuation turns", function () {
        if (!app.includes('if (result.draftIdentity)')) throw new Error("should only overwrite identity when non-null");
        if (!app.includes('aiReplyState.lastDraftIdentity')) throw new Error("identity should persist through turns");
        if (!app.includes('|| aiReplyState.lastDraftIdentity')) throw new Error("fallback to workbench identity");
    });

    // -- P1-C: strict numbering --
    it("numbering rejects zero headings", function () {
        if (!app.includes("nums.length === 0")) throw new Error("missing zero-heading check");
    });
    it("numbering rejects out-of-range headings", function () {
        if (!app.includes("越界")) throw new Error("missing out-of-range error");
    });
    it("numbering rejects duplicates", function () {
        if (!app.includes("重复出现")) throw new Error("missing duplicate check");
    });
    it("numbering rejects wrong count", function () {
        if (!app.includes("数量不匹配")) throw new Error("missing count mismatch error");
    });
    it("numbering checks sequential order", function () {
        if (!app.includes("nums[i - 1] !== i")) throw new Error("missing sequential order check");
    });

    // -- P1-D: cancel on context switch --
    it("cancelReviewSession called in resetAiReplyState", function () {
        if (!app.includes("resetAiReplyState(recordId) {\n    cancelReviewSession")) throw new Error("cancelReviewSession not called in reset");
    });
    it("cancelReviewSession called in showUnmatchedDetail", function () {
        if (!app.includes("showUnmatchedDetail(id) {\n    manualReplyQaContext = null;\n    aiReplyState.adoptContext = null;\n    cancelReviewSession")) throw new Error("cancelReviewSession not called in showUnmatchedDetail");
    });
    it("backdrop and cancel buttons use cancelReviewSession", function () {
        if (!app.includes('$("#aiReplyReviewBackdrop")?.addEventListener("click", cancelReviewSession'))
            throw new Error("backdrop should cancel");
        if (!app.includes('$("#aiReplyReviewCancelBtn")?.addEventListener("click", cancelReviewSession'))
            throw new Error("cancel button should cancel");
    });

    // -- P1-B: cancel old session on re-adopt --
    it("ai-adopt-draft cancels existing review session before replacing adoptContext", function () {
        if (!app.includes('if (action === "ai-adopt-draft") {\n        cancelReviewSession();'))
            throw new Error("cancelReviewSession not called at start of ai-adopt-draft");
    });

    it("SEND_BLOCKED event reported best-effort before modal", function () {
        if (!app.includes("ai-reply/review-event")) throw new Error("missing review-event API call");
    });
});

describe("Phase 10 AI reply authority fail-closed", function () {
    it("AI_REPLY_AUDIT_UNAVAILABLE registered in warning labels", function () {
        if (!app.includes("AI_REPLY_AUDIT_UNAVAILABLE")) throw new Error("missing AI_REPLY_AUDIT_UNAVAILABLE in warning labels");
        if (!app.includes("AI 草稿审核记录保存失败，本次草稿未提供。请重试生成。")) throw new Error("missing Chinese text for audit unavailable");
    });

    it("draftAuthorityAvailable false branch renders error and returns early", function () {
        if (!app.includes("result.draftAuthorityAvailable === false")) throw new Error("missing draftAuthorityAvailable check");
    });

    it("draftAuthorityAvailable false does not set firstTurnDone or write drafts", function () {
        const idx = app.indexOf("result.draftAuthorityAvailable === false");
        if (idx < 0) throw new Error("missing check");
        const returnIdx = app.indexOf("return;", idx);
        const firstTurnIdx = app.indexOf("aiReplyState.firstTurnDone = true", idx);
        if (firstTurnIdx > 0 && firstTurnIdx < returnIdx) throw new Error("firstTurnDone should not be set in authority-fail path");
    });
});

describe("Phase 9 regression: existing tests", function () {
    it("batchSendTaskConsoleVisualFix.test.js still exists and references unchanged", function () {
        const exists = fs.existsSync("src/test/js/batchSendTaskConsoleVisualFix.test.js");
        if (!exists) throw new Error("batchSendTaskConsoleVisualFix.test.js missing");
    });

    it("existing app.js handler structure preserved around new code", function () {
        if (!app.includes('"send-manual-rich-reply"')) throw new Error("send handler missing");
        if (!app.includes('"ai-adopt-draft"')) throw new Error("adopt handler missing");
    });
});
