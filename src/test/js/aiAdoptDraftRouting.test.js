const assert = require("assert");
const { describe, it } = require("node:test");

/** Mirrors ai-adopt-draft send-path branch in app.js handleUnmatchedAction. */
function resolveAiAdoptTarget(sendQaRuleIds) {
    if (sendQaRuleIds && sendQaRuleIds.length > 0) {
        return "composed-reply";
    }
    return "manual-rich-reply";
}

describe("ai adopt draft send path (from app.js semantics)", () => {
    it("routes to manual-rich-reply when lastQaRuleIds is empty", () => {
        assert.strictEqual(resolveAiAdoptTarget([]), "manual-rich-reply");
    });

    it("routes to composed-reply when lastQaRuleIds has matched subset", () => {
        assert.strictEqual(resolveAiAdoptTarget([10, 20]), "composed-reply");
    });
});
