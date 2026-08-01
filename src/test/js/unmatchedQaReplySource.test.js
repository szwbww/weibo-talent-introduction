const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

describe("mail processing reply workflow source", () => {
    it("renders history and all supported reply modes as collapsed workflow details", () => {
        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail mail-history-detail"'));
        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail compose-workbench-section"'));
        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail manual-rich-reply-section"'));
        assert.ok((appJsSource.match(/<details class="detail-section reply-workflow-detail/g) || []).length >= 3);
    });

    it("does not render the unsupported single-rule QA reply", () => {
        assert.ok(!appJsSource.includes("function buildUnmatchedQaReplyHtml"));
        assert.ok(!appJsSource.includes("QA 邮件回复（单规则）"));
        assert.ok(!appJsSource.includes('data-action="send-pending-qa-reply"'));
    });

    it("collapses original body and opens cleaned body by default", () => {
        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail mail-body-section original-mail-body-section"'));
        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail mail-body-section cleaned-mail-body-section" open'));
    });

    it("requires manual click to load the folded auto-reply preview", () => {
        assert.ok(appJsSource.includes("async function loadAutoReplyPreview(recordId)"));
        assert.ok(appJsSource.includes('id="autoReplyPreviewStatus"'));
        assert.ok(appJsSource.includes('id="autoReplyPreviewMeta"'));
        assert.ok(appJsSource.includes("点击按钮后分析来信意图与回复规则"));
        assert.ok(appJsSource.includes("尚未生成自动回复预览"));
        assert.ok(appJsSource.includes('data-action="preview-auto-reply"'));
        assert.ok(!appJsSource.includes("loadAutoReplyPreview(id).catch"));
    });

    it("adds visible page groups and folds operation logs", () => {
        ["基本信息", "邮件正文", "处理与回复", "操作记录"].forEach((title) => {
            assert.ok(appJsSource.includes(`<span>${title}</span>`));
        });
        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail operator-log-section"'));
    });

    it("integrates mail, tags, expert, and controls into one compact overview", () => {
        assert.ok(appJsSource.includes('class="mail-expert-overview"'));
        assert.ok(appJsSource.includes('class="mail-overview-head"'));
        assert.ok(appJsSource.includes('class="mail-expert-overview-expert"'));
        assert.ok(appJsSource.includes('class="mail-technical-detail"'));
        assert.ok(appJsSource.includes('data-action="save-expert-changes"'));
        assert.ok(!appJsSource.includes('data-action="change-operator-status"'));
        assert.ok(!appJsSource.includes('data-action="change-index-level"'));
    });

    it("saves changed expert status and level through their existing endpoints", () => {
        assert.ok(appJsSource.includes('if (action === "save-expert-changes")'));
        assert.ok(appJsSource.includes("const statusChanged = newStatus && newStatus !== currentStatus"));
        assert.ok(appJsSource.includes("const levelChanged = newLevel && newLevel !== currentLevel"));
        assert.ok(appJsSource.includes("body: JSON.stringify({ operatorStatus: newStatus, operatorName })"));
        assert.ok(appJsSource.includes("body: JSON.stringify({ targetLevel: newLevel, operatorName })"));
    });

    it("moves the mailbox scroll container to the processing panel after opening a mail", () => {
        assert.ok(appJsSource.includes("function focusMailboxProcessingPanel()"));
        assert.ok(appJsSource.includes('scrollContainer.scrollTo({ top, behavior: "smooth" })'));
        assert.ok((appJsSource.match(/focusMailboxProcessingPanel\(\);/g) || []).length >= 2);
    });
});
