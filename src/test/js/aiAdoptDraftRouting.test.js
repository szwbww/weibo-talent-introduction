const assert = require("assert");
const { describe, it } = require("node:test");

/** Mirrors manual rich send QA context in app.js handleUnmatchedAction. */
function buildManualRichQaPayload(qaRuleIds, baselineText, editorText) {
    if (!qaRuleIds || qaRuleIds.length === 0) {
        return {};
    }
    return {
        qaRuleIds: [...qaRuleIds],
        edited: editorText.trim() !== (baselineText || "").trim()
    };
}

describe("trust workbench adopt send path (from app.js semantics)", () => {
    it("sends only qaRuleIds without suggestedRuleIds authority fields", () => {
        const payload = buildManualRichQaPayload([10, 20], "Draft body", "Draft body");
        assert.deepStrictEqual(payload.qaRuleIds, [10, 20]);
        assert.strictEqual(payload.edited, false);
        assert.strictEqual(payload.suggestedRuleIds, undefined);
        assert.strictEqual(payload.useVariants, undefined);
        assert.strictEqual(payload.ackSnippetId, undefined);
    });

    it("marks edited when operator changes adopted draft", () => {
        const payload = buildManualRichQaPayload([10], "Draft body", "Draft body edited");
        assert.strictEqual(payload.edited, true);
    });
});
