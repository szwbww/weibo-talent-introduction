const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

describe("composed reply legacy removal (negative contract)", () => {
    it("removed segment ordering and drag/drop handlers", () => {
        assert.ok(!appJsSource.includes("function buildComposedSegments"));
        assert.ok(!appJsSource.includes("function mergeSegmentsToText"));
        assert.ok(!appJsSource.includes("function refreshComposedPreviewFromRules"));
        assert.ok(!appJsSource.includes('data-action="compose-move-up"'));
        assert.ok(!appJsSource.includes('data-action="copy-to-manual-rich-reply"'));
        assert.ok(!appJsSource.includes("useVariants=false"));
        assert.ok(!appJsSource.includes("manualReplyUseVariants"));
    });
});
