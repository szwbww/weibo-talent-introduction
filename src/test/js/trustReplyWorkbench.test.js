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
        assert.match(workbench, /function toggleResolve\(/);
        assert.match(workbench, /function assemble\(/);
        assert.match(workbench, /lockedItems/);
        assert.match(workbench, /rawDraftText/);
        assert.match(workbench, /generationKind/);
        assert.doesNotMatch(app, /composedReplyState|renderComposedReplyWorkbenchHtml|trust-generate-draft/);
    });

    it("renders two tab panels with shared state and fixed completion labels", () => {
        assert.match(workbench, /role="tablist"/);
        assert.match(workbench, /data-action="set-page"/);
        assert.match(workbench, /data-page-panel="facts"/);
        assert.match(workbench, /data-page-panel="frame"/);
        assert.match(workbench, /function setActivePage\(/);
        assert.match(workbench, /aria-selected/);
        assert.match(workbench, /data-role="handling"/);
        assert.match(workbench, /data-role="instruction"/);
        assert.match(workbench, /data-role="version"/);
        assert.match(workbench, /resolve-item/);
        assert.match(workbench, /data-action="toggle-item"/);
        assert.match(workbench, /ANSWER_FROM_OPERATOR_INPUT/);
        assert.match(workbench, /function resolvedVersion\(/);
        assert.match(workbench, /function requestTranslation\(/);
        assert.doesNotMatch(workbench, /data-action="generate-all"/);
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

    it("sends the canonical matrix and frame snapshot instead of flat facts", () => {
        assert.match(workbench, /function serializeRequestFactSelections\(/);
        assert.match(workbench, /requestFactSelections: serializeRequestFactSelections\(\)/);
        assert.match(workbench, /frameSnapshot: state\.frameSnapshot/);
        assert.match(workbench, /function sameFrameSnapshot\(/);
        assert.match(workbench, /function factOwnerById\(/);
        assert.doesNotMatch(workbench, /requestedFactIds/);
        assert.doesNotMatch(workbench, /selectedFactIds/);
        assert.doesNotMatch(workbench, /\[data-role="fact"\]/);
        assert.match(workbench, /trust-reply-workbench-state-v3/);
    });

    it("keeps explicit per-item generation triggers and no mount-time full draft", () => {
        assert.doesNotMatch(workbench, /initialFullDraftSourceVersions/);
        assert.doesNotMatch(workbench, /void generateAll\(\)/);
        assert.match(workbench, /function generateMissingGrounded\(/);
        assert.match(workbench, /function computeReadiness\(/);
        assert.match(workbench, /data-action="assemble"/);
        assert.match(workbench, /operation: "ADJUST_ITEM"/);
        assert.doesNotMatch(workbench, /"FULL_DRAFT"/);
        assert.doesNotMatch(workbench, /data-action="generate-all"/);
    });

    it("only accepts a server assembly for completion and shows preview states", () => {
        assert.match(workbench, /function assemblyIdentityMatches\(/);
        assert.match(workbench, /function previewState\(/);
        assert.match(workbench, /previewState\(\) !== "CURRENT"/);
        assert.match(workbench, /配置预览 · 尚未服务端整合/);
        assert.match(workbench, /服务端整合完成/);
        assert.match(workbench, /配置已变化 · 请重新整合/);
        assert.match(workbench, /data-role="local-preview"/);
        assert.match(workbench, /data-role="raw-preview"/);
    });

    it("exposes per-card fact chips and picker with owner labels", () => {
        assert.match(workbench, /data-action="add-fact"/);
        assert.match(workbench, /data-action="remove-fact"/);
        assert.match(workbench, /data-action="toggle-fact-picker"/);
        assert.match(workbench, /已用于摘要 /);
        assert.match(workbench, /已选择/);
        assert.match(workbench, /保存中/);
        assert.match(workbench, /class="trust-reply-fact-picker-option"/);
    });

    it("keeps page code as thin training/live adapters", () => {
        assert.match(app, /mountAiTrainingTrustReply/);
        assert.match(app, /source: \{ sourceType: "TRAINING_MAIL", sourceId: Number\(mail\.mailRecordId\) \}/);
        assert.match(app, /mountLiveTrustReply/);
        assert.match(app, /source: \{ sourceType: "LIVE_INBOUND", sourceId: Number\(recordId\) \}/);
        assert.match(app, /rawTemplate: assembly\.rawDraftText/);
        assert.match(app, /function buildTrustReplyAssemblySnapshot\(/);
        assert.doesNotMatch(app, /aiTrainingSimulateBtn|aiTrainingSimulateMessages|aiTrainingReplyModel/);
    });
});
