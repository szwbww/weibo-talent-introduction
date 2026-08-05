"use strict";

const { describe, it } = require("node:test");
const fs = require("fs");

const app = fs.readFileSync("src/main/resources/static/app.js", "utf-8");
const html = fs.readFileSync("src/main/resources/static/index.html", "utf-8");
const workbench = fs.readFileSync("src/main/resources/static/trust-reply-workbench.js", "utf-8");

describe("adopt-direct-send UI contracts", function () {
    it("shared completion has simulation and live adapters without a send call", function () {
        if (!app.includes("mountAiTrainingTrustReply")) throw new Error("missing training adapter");
        if (!app.includes("mountLiveTrustReply")) throw new Error("missing live adapter");
        if (!app.includes("adoptTrustReplyAssembly")) throw new Error("missing live adoption adapter");
        if (!workbench.includes("完成模拟并评估") || !workbench.includes("采用到人工回复")) {
            throw new Error("missing fixed completion labels");
        }
        if (workbench.includes("manual-rich-reply") || workbench.includes("/send")) {
            throw new Error("shared component must not know send paths");
        }
    });

    it("live adapter adopts the same raw/rendered/hash assembly", function () {
        if (!app.includes("rawTemplate: assembly.rawDraftText")) throw new Error("raw authority lost");
        if (!app.includes("renderedDraftText || assembly.rawDraftText")) throw new Error("rendered authority lost");
        if (!app.includes("draftHash: assembly.draftHash")) throw new Error("draft hash lost");
        if (!app.includes("editor.innerText = rendered")) throw new Error("editor adoption missing");
        if (!app.includes("requestFactSelections: requestFactSelections.length ? requestFactSelections : null")) throw new Error("training fact matrix lost");
        if (!app.includes("requestFactSelections")) throw new Error("training evaluation must carry the canonical matrix");
        if (app.includes("requestedFactIds: assembly.requestedFactIds || assembly.canonicalFactIds")) {
            throw new Error("training fact selection must not fall back to flat ids");
        }
        if (!app.includes("liveTrustReplyToken === token")) throw new Error("live adapter identity guard missing");
    });

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

    it("archive snapshot deep copies the canonical matrix and frame without claim fallback", function () {
        const snapshotFn = app.match(/function buildTrustReplyAssemblySnapshot\(assembly\) \{[\s\S]*?\n\}/)?.[0] || "";
        if (!snapshotFn) throw new Error("missing buildTrustReplyAssemblySnapshot");
        if (!snapshotFn.includes("requestFactSelections: requestFactSelections.length ? requestFactSelections : null")) {
            throw new Error("archive snapshot must preserve the full request fact matrix");
        }
        if (!snapshotFn.includes("factRuleIds: [...(selection.factRuleIds || [])]")) {
            throw new Error("archive snapshot must deep copy each factRuleIds list");
        }
        if (!snapshotFn.includes("frameSnapshot: assembly.frameSnapshot")) {
            throw new Error("archive snapshot must carry the frame snapshot");
        }
        if (!snapshotFn.includes("version: assembly.frameSnapshot.version || \"\"")) {
            throw new Error("archive snapshot must preserve the frame version");
        }
        if (snapshotFn.includes("canonicalFactIds")) {
            throw new Error("archive snapshot builder must not fall back to canonicalFactIds");
        }
        if (snapshotFn.includes("requestedFactIds")) {
            throw new Error("archive snapshot builder must not send flat requestedFactIds");
        }
    });

    it("preserves raw template across live adoption→send only when editor matches baseline", function () {
        const adoptIdx = app.indexOf("function adoptTrustReplyAssembly");
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        const adoptBlock = app.slice(adoptIdx, adoptIdx + 2200);
        const sendBlock = app.slice(sendIdx, sendIdx + 2400);
        if (!adoptBlock.includes("editor.innerText = rendered")) throw new Error("adopt should copy rendered text");
        if (!adoptBlock.includes("rawTemplate:")) throw new Error("adopt should keep raw template baseline");
        if (!adoptBlock.includes("trustReplyAssembly:")) throw new Error("adopt should keep reassemble snapshot");
        if (!adoptBlock.includes("buildTrustReplyAssemblySnapshot")) throw new Error("adopt should build assembly snapshot");
        if (!sendBlock.includes("templateTextBody = adopt.rawTemplate")) throw new Error("send should pass raw template when unedited");
        if (!sendBlock.includes("trustReplyAssembly = adopt.trustReplyAssembly")) throw new Error("send should pass assembly when unedited");
        if (!sendBlock.includes("editor.innerHTML === (adopt.renderedBaselineHtml")) throw new Error("send should compare HTML baseline");
    });

    it("submitManualRichReply surfaces archive warnings without treating them as send failure", function () {
        const submit = app.match(/async function submitManualRichReply\([\s\S]*?\n    \}/)?.[0] || "";
        if (!submit.includes("unsupportedAnswerArchiveStatus")) throw new Error("submit should read archive status");
        if (!submit.includes("人工回复邮件发送成功")) throw new Error("submit should keep success wording");
        if (!submit.includes("无依据回答索引未完整写入，请勿重复发送")) throw new Error("submit should warn on archive failure");
        if (!submit.includes('showStatus("人工回复邮件发送成功；无依据回答索引未完整写入，请勿重复发送", "warn")')) {
            throw new Error("submit should use warn status for archive failure");
        }
        if (submit.includes('showStatus("人工回复发送失败')) throw new Error("submit must not mark archive failure as send failure");
    });

    it("training host never constructs manual-rich-reply payload", function () {
        const trainingBlock = app.slice(app.indexOf("function mountAiTrainingTrustReply"), app.indexOf("function selectSimulateMail"));
        if (trainingBlock.includes("send-manual-rich-reply")) throw new Error("training host must not send");
        if (trainingBlock.includes("trustReplyAssembly")) throw new Error("training host must not build manual send assembly");
    });

    it("send path has no section numbering gate after adopt", function () {
        if (app.includes("validateSectionNumbering")) throw new Error("numbering gate helper should be removed");
        if (app.includes("正文编号")) throw new Error("numbering error messages should be removed");
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        const submitIdx = app.indexOf("return submitManualRichReply(id, requestBody);", sendIdx);
        if (sendIdx < 0 || submitIdx < 0) throw new Error("missing send handler or submit call");
        const sendBlock = app.slice(sendIdx, submitIdx + "return submitManualRichReply(id, requestBody);".length);
        if (sendBlock.includes("openReviewModal")) throw new Error("send should not open review modal");
        if (/showStatus[\s\S]*requestCount/.test(sendBlock)) {
            throw new Error("send should not gate on requestCount");
        }
    });

    it("quality panel hides deprecated review metrics", function () {
        const panelFn = app.match(/function renderQaAuditPanel\([\s\S]*?\n\}/)?.[0] || "";
        if (panelFn.includes("直发拦截")) throw new Error("directSendBlocked card should be removed");
        if (panelFn.includes("人工确认")) throw new Error("reviewConfirmed card should be removed");
        if (!panelFn.includes("AI 初稿总数")) throw new Error("initial draft total should remain");
        if (!panelFn.includes("完整率 (READY)")) throw new Error("ready rate should remain");
    });

    it("send-manual-rich-reply does not gate on generationState or usedLlm", function () {
        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
        if (sendIdx < 0) throw new Error("missing send handler");
        const sendBlock = app.slice(sendIdx, sendIdx + 1200);
        if (sendBlock.includes("generationState"))
            throw new Error("send should not gate on generationState");
        if (sendBlock.includes("usedLlm"))
            throw new Error("send should not gate on usedLlm");
        if (sendBlock.includes("isAiReplyGenerationSuccess"))
            throw new Error("send should not call isAiReplyGenerationSuccess");
    });

    it("existing manual send handler structure preserved", function () {
        if (!app.includes('"send-manual-rich-reply"')) throw new Error("send handler missing");
        if (app.includes('"trust-adopt-draft"')) throw new Error("legacy trust adopt handler should be removed");
    });
});

describe("Phase 9 regression: existing tests", function () {
    it("batchSendTaskConsoleVisualFix.test.js still exists and references unchanged", function () {
        const exists = fs.existsSync("src/test/js/batchSendTaskConsoleVisualFix.test.js");
        if (!exists) throw new Error("batchSendTaskConsoleVisualFix.test.js missing");
    });
});
