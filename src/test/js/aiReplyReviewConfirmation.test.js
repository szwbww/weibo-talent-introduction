"use strict";

const { describe, it } = require("node:test");
const fs = require("fs");

const app = fs.readFileSync("src/main/resources/static/app.js", "utf-8");
const html = fs.readFileSync("src/main/resources/static/index.html", "utf-8");

describe("adopt-direct-send UI contracts", function () {
    it("HTML does not contain aiReplyReviewModal", function () {
        if (html.includes("aiReplyReviewModal")) throw new Error("review modal should be removed");
        if (html.includes("aiReplyReviewList")) throw new Error("review list should be removed");
        if (html.includes("aiReplyReviewConfirmBtn")) throw new Error("review confirm btn should be removed");
    });

    it("app.js does not contain review-event or review confirmation payload", function () {
        if (app.includes("ai-reply/review-event")) throw new Error("review-event API should be removed");
        if (app.includes("aiReviewConfirmation")) throw new Error("aiReviewConfirmation should be removed");
        if (app.includes('requestBody.replySource = "AI_DRAFT"')) throw new Error("replySource AI_DRAFT should be removed");
        if (app.includes("aiReplyReviewState")) throw new Error("review state machine should be removed");
        if (app.includes("function openReviewModal")) throw new Error("openReviewModal should be removed");
        if (app.includes("function cancelReviewSession")) throw new Error("cancelReviewSession should be removed");
        if (app.includes("function buildIntentReviewItems")) throw new Error("buildIntentReviewItems should be removed");
        if (app.includes("draftAuthorityAvailable")) throw new Error("draftAuthorityAvailable should be removed");
        if (app.includes("lastDraftIdentity")) throw new Error("lastDraftIdentity should be removed");
        if (app.includes("draftIdentity")) throw new Error("draftIdentity should be removed from frontend");
    });

    it("adopt button uses fixed label 采用此草稿", function () {
        const draftBubble = app.match(/function appendAiChatDraftBubble\([\s\S]*?\n\}/)?.[0] || "";
        if (!draftBubble.includes("采用此草稿")) throw new Error("missing adopt label");
        if (draftBubble.includes("采用并人工补充")) throw new Error("conditional adopt label should be removed");
    });

    it("has submitManualRichReply extracted function", function () {
        if (!app.includes("function submitManualRichReply(")) throw new Error("missing submitManualRichReply");
    });

    it("send-manual-rich-reply calls submitManualRichReply directly", function () {
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        if (sendIdx < 0) throw new Error("missing send handler");
        const sendBlock = app.slice(sendIdx, sendIdx + 2200);
        if (!sendBlock.includes("return submitManualRichReply(id, requestBody);")) {
            throw new Error("send should call submitManualRichReply directly");
        }
        if (sendBlock.includes("openReviewModal")) throw new Error("send should not open review modal");
    });

    it("preserves raw template across adopt→send only when editor matches baseline", function () {
        const adoptIdx = app.indexOf('if (action === "ai-adopt-draft")');
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        const adoptBlock = app.slice(adoptIdx, adoptIdx + 1600);
        const sendBlock = app.slice(sendIdx, sendIdx + 2200);
        if (!adoptBlock.includes("editor.innerText = rendered")) throw new Error("adopt should copy rendered text");
        if (!adoptBlock.includes("rawTemplate:")) throw new Error("adopt should keep raw template baseline");
        if (!sendBlock.includes("templateTextBody = adopt.rawTemplate")) throw new Error("send should pass raw template when unedited");
        if (!sendBlock.includes("editor.innerHTML === (adopt.renderedBaselineHtml")) throw new Error("send should compare HTML baseline");
    });

    it("send path has no section numbering gate after adopt", function () {
        if (app.includes("validateSectionNumbering")) throw new Error("numbering gate helper should be removed");
        if (app.includes("正文编号")) throw new Error("numbering error messages should be removed");
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        const sendBlock = app.slice(sendIdx, sendIdx + 2200);
        if (sendBlock.includes("requestCount") && sendBlock.includes("showStatus") && sendBlock.indexOf("requestCount") < sendBlock.lastIndexOf("showStatus")) {
            throw new Error("send should not gate on requestCount");
        }
        const payloadEnd = sendBlock.indexOf("requestBody.useVariants");
        const submitIdx = sendBlock.indexOf("return submitManualRichReply");
        if (payloadEnd < 0 || submitIdx < 0 || submitIdx - payloadEnd > 120) {
            throw new Error("submitManualRichReply should follow payload assembly without intermediate gates");
        }
    });

    it("quality panel hides deprecated review metrics", function () {
        const panelFn = app.match(/function renderQaAuditPanel\([\s\S]*?\n\}/)?.[0] || "";
        if (panelFn.includes("直发拦截")) throw new Error("directSendBlocked card should be removed");
        if (panelFn.includes("人工确认")) throw new Error("reviewConfirmed card should be removed");
        if (!panelFn.includes("AI 初稿总数")) throw new Error("initial draft total should remain");
        if (!panelFn.includes("完整率 (READY)")) throw new Error("ready rate should remain");
    });

    it("existing handler structure preserved", function () {
        if (!app.includes('"send-manual-rich-reply"')) throw new Error("send handler missing");
        if (!app.includes('"ai-adopt-draft"')) throw new Error("adopt handler missing");
    });
});

describe("Phase 9 regression: existing tests", function () {
    it("batchSendTaskConsoleVisualFix.test.js still exists and references unchanged", function () {
        const exists = fs.existsSync("src/test/js/batchSendTaskConsoleVisualFix.test.js");
        if (!exists) throw new Error("batchSendTaskConsoleVisualFix.test.js missing");
    });
});
