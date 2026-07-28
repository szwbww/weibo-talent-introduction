const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const appPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const workbenchPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const app = fs.readFileSync(appPath, "utf-8");
const workbench = fs.readFileSync(workbenchPath, "utf-8");

describe("shared trust reply workbench", () => {
    it("keeps the component as the only workbench implementation", () => {
        assert.match(workbench, /function mount\(host, options\)/);
        assert.match(workbench, /function requestJson\(/);
        assert.match(workbench, /function requestSse\(/);
        assert.match(workbench, /function resetVersions\(/);
        assert.match(workbench, /function toggleLock\(/);
        assert.match(workbench, /function assemble\(/);
        assert.match(workbench, /lockedItems:/);
        assert.match(workbench, /rawDraftText/);
        assert.match(workbench, /generationKind/);
        assert.doesNotMatch(app, /composedReplyState|renderComposedReplyWorkbenchHtml|trust-generate-draft/);
    });

    it("renders item controls and fixed mode completion labels", () => {
        assert.match(workbench, /data-role="handling"/);
        assert.match(workbench, /data-role="instruction"/);
        assert.match(workbench, /data-role="version"/);
        assert.match(workbench, /data-action="lock-item"/);
        assert.match(workbench, /data-action="cancel-generation"/);
        assert.match(workbench, /模拟 · 不外发/);
        assert.match(workbench, /正式回复/);
        assert.match(workbench, /完成模拟并评估/);
        assert.match(workbench, /采用到人工回复/);
        assert.doesNotMatch(workbench, /mode-switch|mode-selector|modeToggle/i);
    });

    it("uses server identity and does not locally compose answer text", () => {
        assert.match(workbench, /sourceId: Number\(options\.source\.sourceId\)/);
        assert.match(workbench, /expectedSourceVersion: state\.sourceVersion/);
        assert.match(workbench, /expectedEvidenceSetVersion: state\.evidenceSetVersion/);
        assert.match(workbench, /rawDraftText \|\|/);
        assert.doesNotMatch(workbench, /answers\.join|dedupe|truncate|LLM rewrite/i);
    });

    it("keeps page code as thin training/live adapters", () => {
        assert.match(app, /mountAiTrainingTrustReply/);
        assert.match(app, /source: \{ sourceType: "TRAINING_MAIL", sourceId: Number\(mail\.mailRecordId\) \}/);
        assert.match(app, /mountLiveTrustReply/);
        assert.match(app, /source: \{ sourceType: "LIVE_INBOUND", sourceId: Number\(recordId\) \}/);
        assert.match(app, /rawTemplate: assembly\.rawDraftText/);
        assert.doesNotMatch(app, /aiTrainingSimulateBtn|aiTrainingSimulateMessages|aiTrainingReplyModel/);
    });
});
